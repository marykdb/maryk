package maryk.datastore.foundationdb.processors.helpers

import kotlinx.coroutines.runBlocking
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.datastore.foundationdb.FoundationDBReadBudget
import maryk.datastore.foundationdb.foundationDbFuture
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.shared.simpleScalarReadProperty
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.KeyValue
import maryk.foundationdb.Range
import maryk.foundationdb.StreamingMode
import maryk.foundationdb.Transaction
import maryk.foundationdb.async.AsyncIterable
import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.async.CloseableAsyncIterator
import maryk.lib.extensions.compare.compareTo

/** Native reads are started together, but decoded in request order in the owning transaction. */
internal class PreparedRecordRead(
    private var points: List<Pair<ByteArray, FdbFuture<ByteArray?>>>,
    private var ranges: List<PreparedRangeRead>,
    private val budget: FoundationDBReadBudget,
    private val currentSoftDeleteKey: ByteArray? = null,
) {
    private var preparedRows: List<KeyValue>? = null
    private var released = false

    /** Structural rereads share the record limit already charged by these rows. */
    val readBudget: FoundationDBReadBudget get() = budget

    /** Same logical row bytes charged by the original read, before raw buffers are released. */
    val materializedBytes: Long
        get() = preparedRows?.sumOf { it.key.size.toLong() + it.value.size } ?: 0L

    fun awaitRows(): List<KeyValue> = preparedRows ?: runBlocking { awaitRowsSuspending() }

    suspend fun awaitRowsSuspending(): List<KeyValue> {
        check(!released) { "Prepared record read has been released" }
        preparedRows?.let { return it }
        return try {
            val rows = if (points.isEmpty() && ranges.size == 1) ranges.single().awaitRows() else {
                val collected = ArrayList<KeyValue>()
                points.forEach { (key, future) -> future.await()?.let { value ->
                    val row = KeyValue(key, value)
                    budget.consume(row)
                    collected += row
                } }
                ranges.forEach { collected.addAll(it.awaitRows()) }
                collected.sortWith { first, second -> first.key.compareTo(second.key) }
                collected
            }
            preparedRows = rows
            rows
        } finally {
            cancel()
        }
    }

    /** Current ranges already contain this marker; retain their prefix rows for later decoding. */
    suspend fun awaitCurrentSoftDeleteValue(): ByteArray? {
        check(!released) { "Prepared record read has been released" }
        val key = checkNotNull(currentSoftDeleteKey)
        preparedRows?.let { rows -> return rows.firstOrNull { it.key.contentEquals(key) }?.value }
        points.firstOrNull { it.first.contentEquals(key) }?.let { return it.second.await() }
        return ranges.single().awaitPrefixValue(key)
    }

    fun cancel() {
        points.forEach { it.second.cancel() }
        ranges.forEach { it.cancel() }
    }

    /** Discard raw rows after decoding, even while the request still owns this batch. */
    fun release() {
        released = true
        preparedRows = null
        try {
            cancel()
        } finally {
            points = emptyList()
            ranges = emptyList()
        }
    }
}

internal class PreparedRecordSelection(
    val qualifiers: List<ByteArray>?,
    val contiguousEnd: ByteArray?,
)

private val fullRecordSelection = PreparedRecordSelection(null, null)

/** Structural replay needs complete selected subtrees, not unrelated record payloads. */
internal fun <DM : IsRootDataModel> DM.prepareStructuralRecordRead(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: ByteArray,
    select: RootPropRefGraph<DM>,
    toVersion: ULong?,
    requestBudget: FoundationDBReadBudget,
): PreparedRecordRead {
    val budget = FoundationDBReadBudget(tableDirs.dataStore.executionConfiguration.maxRecordReadBytes, requestBudget)
    val prefix = if (toVersion == null) tableDirs.tablePrefix else {
        (tableDirs as? HistoricTableDirectories)?.historicTablePrefix
            ?: throw RequestException("No historic table present so cannot use `toVersion` on get")
    }
    val recordPrefix = packKey(prefix, key)
    val qualifiers = select.properties.map { node ->
        (this[node.index] ?: throw RequestException("Unknown structural root ${node.index}")).ref().toStorageByteArray()
    } + listOf(byteArrayOf(SOFT_DELETE_INDICATOR))
    val ranges = ArrayList<PreparedRangeRead>(qualifiers.size)
    val marker = recordPrefix to tr.get(recordPrefix)
    try {
        for (qualifier in qualifiers) {
            val storedQualifier = if (toVersion == null) qualifier else encodeZeroFreeUsing01(qualifier)
            ranges += tr.prepareBoundedRows(Range.startsWith(packKey(recordPrefix, storedQualifier)), budget)
        }
        return PreparedRecordRead(listOf(marker), ranges, budget)
    } catch (error: Throwable) {
        try { marker.second.cancel() } finally { ranges.forEach { it.cancel() } }
        throw error
    }
}

/** One immutable selection layout can serve every record in the same request. */
internal fun <DM : IsRootDataModel> DM.prepareRecordSelection(
    select: RootPropRefGraph<DM>?,
    toVersion: ULong?,
): PreparedRecordSelection {
    val selected = select?.properties ?: if (toVersion != null) this.toList() else null
    if (selected == null || selected.size !in 1..16) return fullRecordSelection
    for (property in selected) {
        if (simpleScalarReadProperty(property) == null) {
            return fullRecordSelection
        }
    }
    val softDeleteQualifier = byteArrayOf(SOFT_DELETE_INDICATOR)
    val qualifiers = ArrayList<ByteArray>(selected.size + 1)
    var lastQualifier = softDeleteQualifier
    for (property in selected) {
        val qualifier = (property as IsDefinitionWrapper<*, *, *, *>).ref().toStorageByteArray()
        qualifiers.add(qualifier)
        if (qualifier.compareTo(lastQualifier) > 0) lastQualifier = qualifier
    }
    qualifiers.add(softDeleteQualifier)
    var contiguousEnd = if (toVersion == null) lastQualifier else null
    if (contiguousEnd != null) {
        for (property in this) {
            var isSelected = false
            // At most sixteen scalar properties; avoid a temporary set and duplicate qualifiers.
            for (selection in selected) {
                if ((selection as IsDefinitionWrapper<*, *, *, *>).index == property.index) {
                    isSelected = true
                    break
                }
            }
            if (!isSelected && property.ref().toStorageByteArray().compareTo(lastQualifier) <= 0) {
                contiguousEnd = null
                break
            }
        }
    }
    return PreparedRecordSelection(qualifiers, contiguousEnd)
}

internal fun <DM : IsRootDataModel> DM.prepareRecordRead(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: ByteArray,
    select: RootPropRefGraph<DM>?,
    toVersion: ULong?,
    requestBudget: FoundationDBReadBudget? = null,
    reuseCurrentSoftDelete: Boolean = false,
): PreparedRecordRead {
    if (toVersion != null && tableDirs !is HistoricTableDirectories) {
        throw RequestException("No historic table present so cannot use `toVersion` on get")
    }
    return prepareRecordRead(tr, tableDirs, key, prepareRecordSelection(select, toVersion), toVersion, requestBudget, reuseCurrentSoftDelete)
}

internal fun <DM : IsRootDataModel> DM.prepareRecordRead(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: ByteArray,
    selection: PreparedRecordSelection,
    toVersion: ULong?,
    requestBudget: FoundationDBReadBudget? = null,
    reuseCurrentSoftDelete: Boolean = false,
): PreparedRecordRead {
    val budget = FoundationDBReadBudget(tableDirs.dataStore.executionConfiguration.maxRecordReadBytes, requestBudget)
    val prefix = if (toVersion == null) tableDirs.tablePrefix else {
        (tableDirs as? HistoricTableDirectories)?.historicTablePrefix
            ?: throw RequestException("No historic table present so cannot use `toVersion` on get")
    }
    val recordPrefix = packKey(prefix, key)
    val softDeleteKey = if (reuseCurrentSoftDelete && toVersion == null) packKey(recordPrefix, byteArrayOf(SOFT_DELETE_INDICATOR)) else null
    val qualifiers = selection.qualifiers
        ?: return PreparedRecordRead(emptyList(), listOf(tr.prepareBoundedRows(Range.startsWith(recordPrefix), budget)), budget, softDeleteKey)
    val contiguousEnd = selection.contiguousEnd
    if (toVersion == null && contiguousEnd != null) {
        return PreparedRecordRead(emptyList(), listOf(tr.prepareBoundedRows(Range(
            recordPrefix, packKey(recordPrefix, contiguousEnd, byteArrayOf(0)),
        ), budget)), budget, softDeleteKey)
    }
    val marker = recordPrefix to tr.get(recordPrefix)
    return if (toVersion == null) {
        PreparedRecordRead(listOf(marker) + qualifiers.map { qualifier ->
            val storageKey = packKey(recordPrefix, qualifier)
            storageKey to tr.get(storageKey)
        }, emptyList(), budget, softDeleteKey)
    } else {
        val reversedVersion = toVersion.toReversedVersionBytes()
        PreparedRecordRead(listOf(marker), qualifiers.map { qualifier ->
            val qualifierPrefix = packKey(recordPrefix, encodeZeroFreeUsing01(qualifier))
            tr.prepareBoundedRows(Range(
                packKey(qualifierPrefix, byteArrayOf(0), reversedVersion),
                packKey(qualifierPrefix, byteArrayOf(1)),
            ), budget, limit = 1)
        }, budget)
    }
}

internal fun Transaction.boundedRows(range: Range, budget: FoundationDBReadBudget, limit: Int = 0): FdbFuture<List<KeyValue>> =
    foundationDbFuture { getRange(range, limit).collectBoundedRows(budget) }

/** Start native read-ahead immediately, but collect in the owning request instead of another coroutine. */
internal fun Transaction.prepareBoundedRows(range: Range, budget: FoundationDBReadBudget, limit: Int = 0) =
    PreparedRangeRead(getRange(range, limit).iterator(), budget)

internal class PreparedRangeRead(
    private val iterator: AsyncIterator<KeyValue>,
    private val budget: FoundationDBReadBudget,
) {
    private var released = false
    private val rows = ArrayList<KeyValue>()
    private var ready = try {
        iterator.onHasNext()
    } catch (error: Throwable) {
        try { cancel() } catch (cleanup: Throwable) { if (cleanup !== error) error.addSuppressed(cleanup) }
        throw error
    }

    /** The record marker and first following row decide whether its soft-delete key exists. */
    suspend fun awaitPrefixValue(key: ByteArray): ByteArray? {
        check(!released) { "Prepared range read has been released" }
        var failure: Throwable? = null
        try {
            for (row in rows) {
                val order = row.key.compareTo(key)
                if (order >= 0) return if (order == 0) row.value else null
            }
            while (ready.await()) {
                val row = iterator.next()
                rows += row
                ready = iterator.onHasNext()
                val order = row.key.compareTo(key)
                if (order >= 0) return if (order == 0) row.value else null
            }
            return null
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            // Keep a successful iterator open for the normal bounded collector.
            failure?.let { primary ->
                try { cancel() } catch (cleanup: Throwable) {
                    if (cleanup !== primary) primary.addSuppressed(cleanup)
                }
            }
        }
    }

    suspend fun awaitRows(): List<KeyValue> {
        check(!released) { "Prepared range read has been released" }
        var failure: Throwable? = null
        try {
            // Filter peeks retain only the prefix. Charge it once when materializing the record.
            var prefixIndex = 0
            while (prefixIndex < rows.size) {
                budget.consume(rows[prefixIndex++])
            }
            while (ready.await()) {
                val row = iterator.next()
                budget.consume(row)
                rows += row
                ready = iterator.onHasNext()
            }
            return rows
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try { cancel() } catch (cleanup: Throwable) {
                val primary = failure
                if (primary == null) throw cleanup
                if (cleanup !== primary) primary.addSuppressed(cleanup)
            }
        }
    }

    fun cancel() {
        if (released) return
        released = true
        if (iterator is CloseableAsyncIterator<KeyValue>) iterator.close() else iterator.cancel()
    }
}

internal suspend fun AsyncIterable<KeyValue>.collectBoundedRows(budget: FoundationDBReadBudget): List<KeyValue> =
    PreparedRangeRead(iterator(), budget).awaitRows()

/** A full single current record is consumed completely; other read shapes keep their existing paths. */
internal fun prepareCurrentFullRecordRead(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: ByteArray,
    requestBudget: FoundationDBReadBudget,
    filterSoftDeleted: Boolean,
): PreparedRecordRead {
    val budget = FoundationDBReadBudget(tableDirs.dataStore.executionConfiguration.maxRecordReadBytes, requestBudget)
    val prefix = packKey(tableDirs.tablePrefix, key)
    val softDeleteKey = if (filterSoftDeleted) packKey(prefix, byteArrayOf(SOFT_DELETE_INDICATOR)) else null
    val iterator = tr.getRange(Range.startsWith(prefix), 0, false, StreamingMode.WANT_ALL).iterator()
    return PreparedRecordRead(emptyList(), listOf(PreparedRangeRead(iterator, budget)), budget, softDeleteKey)
}

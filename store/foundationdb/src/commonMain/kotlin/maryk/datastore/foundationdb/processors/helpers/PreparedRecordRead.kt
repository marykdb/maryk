package maryk.datastore.foundationdb.processors.helpers

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.datastore.foundationdb.FoundationDBReadBudget
import maryk.datastore.foundationdb.foundationDbFuture
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.SOFT_DELETE_INDICATOR
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.KeyValue
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.foundationdb.async.AsyncIterable
import maryk.foundationdb.async.asFlow
import maryk.lib.extensions.compare.compareTo

/** Native reads are started together, but decoded in request order in the owning transaction. */
internal class PreparedRecordRead(
    private var points: List<Pair<ByteArray, FdbFuture<ByteArray?>>>,
    private var ranges: List<FdbFuture<List<KeyValue>>>,
    private val budget: FoundationDBReadBudget,
) {
    private var preparedRows: List<KeyValue>? = null
    private var released = false

    /** Same logical row bytes charged by the original read, before raw buffers are released. */
    val materializedBytes: Long
        get() = preparedRows?.sumOf { it.key.size.toLong() + it.value.size } ?: 0L

    fun awaitRows(): List<KeyValue> = preparedRows ?: runBlocking { awaitRowsSuspending() }

    suspend fun awaitRowsSuspending(): List<KeyValue> {
        check(!released) { "Prepared record read has been released" }
        preparedRows?.let { return it }
        return try {
            val rows = if (points.isEmpty() && ranges.size == 1) ranges.single().await() else {
                val collected = ArrayList<KeyValue>()
                points.forEach { (key, future) -> future.await()?.let { value ->
                    val row = KeyValue(key, value)
                    budget.consume(row)
                    collected += row
                } }
                ranges.forEach { collected.addAll(it.await()) }
                collected.sortWith { first, second -> first.key.compareTo(second.key) }
                collected
            }
            preparedRows = rows
            rows
        } finally {
            cancel()
        }
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

internal fun <DM : IsRootDataModel> DM.prepareRecordRead(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: ByteArray,
    select: RootPropRefGraph<DM>?,
    toVersion: ULong?,
    requestBudget: FoundationDBReadBudget? = null,
): PreparedRecordRead {
    val budget = FoundationDBReadBudget(tableDirs.dataStore.executionConfiguration.maxRecordReadBytes, requestBudget)
    val prefix = if (toVersion == null) tableDirs.tablePrefix else {
        (tableDirs as? HistoricTableDirectories)?.historicTablePrefix
            ?: throw RequestException("No historic table present so cannot use `toVersion` on get")
    }
    val recordPrefix = packKey(prefix, key)
    // A scalar qualifier has no child rows. Collections and nested graph selections
    // retain the full range decoder, including size/type/deletion markers.
    val selected = select?.properties ?: if (toVersion != null) this.toList() else null
    val scalarProperties = selected?.takeIf { properties ->
        properties.size in 1..16 && properties.all {
            it is IsDefinitionWrapper<*, *, *, *> && it.definition is IsSimpleValueDefinition<*, *>
        }
    }
    if (scalarProperties == null) {
        return PreparedRecordRead(emptyList(), listOf(tr.boundedRows(Range.startsWith(recordPrefix), budget)), budget)
    }

    val qualifiers = scalarProperties.map {
        (it as IsDefinitionWrapper<*, *, *, *>).ref().toStorageByteArray()
    } + listOf(byteArrayOf(SOFT_DELETE_INDICATOR))
    if (toVersion == null) {
        val lastQualifier = qualifiers.maxWith { first, second -> first.compareTo(second) }
        val selectedIndexes = scalarProperties.map { (it as IsDefinitionWrapper<*, *, *, *>).index }.toSet()
        val contiguousSelection = this.all { property ->
            property.ref().toStorageByteArray().compareTo(lastQualifier) > 0 || property.index in selectedIndexes
        }
        if (contiguousSelection) {
            // A leading selection plus the record/delete markers fits in one range.
            // Gapped selections use parallel point reads to avoid intervening payloads.
            return PreparedRecordRead(emptyList(), listOf(tr.boundedRows(Range(
                recordPrefix, packKey(recordPrefix, lastQualifier, byteArrayOf(0)),
            ), budget)), budget)
        }
    }
    val marker = recordPrefix to tr.get(recordPrefix)
    return if (toVersion == null) {
        PreparedRecordRead(listOf(marker) + qualifiers.map { qualifier ->
            val storageKey = packKey(recordPrefix, qualifier)
            storageKey to tr.get(storageKey)
        }, emptyList(), budget)
    } else {
        val reversedVersion = toVersion.toReversedVersionBytes()
        PreparedRecordRead(listOf(marker), qualifiers.map { qualifier ->
            val qualifierPrefix = packKey(recordPrefix, encodeZeroFreeUsing01(qualifier))
            // Separator 0 precedes all child qualifiers. Keep the visible delete marker
            // rather than falling through to an older value.
            tr.boundedRows(Range(
                packKey(qualifierPrefix, byteArrayOf(0), reversedVersion),
                packKey(qualifierPrefix, byteArrayOf(1)),
            ), budget, limit = 1)
        }, budget)
    }
}

internal fun Transaction.boundedRows(range: Range, budget: FoundationDBReadBudget, limit: Int = 0): FdbFuture<List<KeyValue>> =
    foundationDbFuture { getRange(range, limit).collectBoundedRows(budget) }

/** Keep range materialization bounded and let asFlow close the cursor on every exit. */
internal suspend fun AsyncIterable<KeyValue>.collectBoundedRows(budget: FoundationDBReadBudget): List<KeyValue> {
    val rows = mutableListOf<KeyValue>()
    asFlow().collect { row ->
        budget.consume(row)
        rows += row
    }
    return rows
}

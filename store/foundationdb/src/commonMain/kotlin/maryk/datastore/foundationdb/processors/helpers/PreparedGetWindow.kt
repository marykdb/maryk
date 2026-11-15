package maryk.datastore.foundationdb.processors.helpers

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.requests.GetRequest
import maryk.datastore.foundationdb.FoundationDBReadBudget
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.foundationdb.processors.isCurrentSoftDeleted
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction

internal const val GET_READ_WINDOW_SIZE = 8

/** Keep preparation policy separate from ownership and ordered record decoding. */
internal class GetReadPreparation<DM : IsRootDataModel>(
    private val request: GetRequest<DM>,
    private val tableDirs: IsTableDirectories,
    private val budget: FoundationDBReadBudget,
) {
    val singleCurrentKey = request.keys.singleOrNull()?.takeIf { request.toVersion == null }
    private val hasRows = request.select?.properties?.isEmpty() != true
    private val nonemptyProjection = request.select?.properties?.isNotEmpty() == true
    private val selection = if (hasRows) request.dataModel.prepareRecordSelection(request.select, request.toVersion) else null
    private val reuseSoftDelete = request.keys.size > 1 || (singleCurrentKey != null && nonemptyProjection && request.filterSoftDeleted)

    /** Only the previously proven shapes may overlap schema validation. */
    fun prepareEarly(tr: Transaction): PreparedGetWindow<DM>? {
        val currentBatch = request.keys.size > 1 && request.toVersion == null && nonemptyProjection && selection?.qualifiers != null
        val historicalSingle = request.keys.size == 1 && request.toVersion != null &&
            request.where == null && request.aggregations == null && nonemptyProjection && selection?.qualifiers != null
        val keys = when {
            currentBatch -> request.keys.take(GET_READ_WINDOW_SIZE).distinct()
            singleCurrentKey != null || historicalSingle -> request.keys
            else -> return null
        }
        return PreparedGetWindow.prepare(keys) { key ->
            prepareRecord(tr, key, includeRows = currentBatch || historicalSingle || nonemptyProjection)
        }
    }

    fun prepareWindow(tr: Transaction, keys: List<Key<DM>>, early: PreparedGetWindow<DM>?): PreparedGetWindow<DM> =
        PreparedGetWindow.prepare(keys) { key -> prepareRecord(tr, key, early?.take(key) ?: PreparedGetRecord()) }

    /** A duplicate rejected by the copy cache must acquire fresh native reads. */
    fun prepareRecord(
        tr: Transaction,
        key: Key<DM>,
        record: PreparedGetRecord = PreparedGetRecord(),
        includeRows: Boolean = hasRows,
    ): PreparedGetRecord {
        try {
            if (request.toVersion == null && record.creation == null) {
                record.creation = tr.get(packKey(tableDirs.keysPrefix, key.bytes))
            }
            if (includeRows && record.read == null) {
                record.read = request.dataModel.prepareRecordRead(tr, tableDirs, key.bytes, checkNotNull(selection),
                    request.toVersion, budget, reuseCurrentSoftDelete = reuseSoftDelete)
                record.reuseSoftDeleteFromRead = reuseSoftDelete && request.toVersion == null
            }
            if (request.toVersion == null && request.filterSoftDeleted && !hasRows &&
                request.keys.size > 1 && record.softDelete == null) {
                record.softDelete = tr.get(packKey(tableDirs.tablePrefix, key.bytes, byteArrayOf(SOFT_DELETE_INDICATOR)))
            }
            return record
        } catch (error: Throwable) {
            try { record.release() } catch (cleanup: Throwable) { error.addCleanupFailure(cleanup) }
            throw error
        }
    }
}

/** All native work for one key moves between owners together. */
internal class PreparedGetRecord {
    var creation: FdbFuture<ByteArray?>? = null
    var read: PreparedRecordRead? = null
    var softDelete: FdbFuture<ByteArray?>? = null
    var reuseSoftDeleteFromRead = false
    private var released = false

    suspend fun currentSoftDeleted(tableDirs: IsTableDirectories, key: ByteArray, decryptValue: DecryptValue?): Boolean? =
        when {
            reuseSoftDeleteFromRead -> isCurrentSoftDeleted(checkNotNull(read).awaitCurrentSoftDeleteValue(), tableDirs, key, decryptValue)
            softDelete != null -> isCurrentSoftDeleted(softDelete!!.await(), tableDirs, key, decryptValue)
            else -> null
        }

    fun release() {
        if (released) return
        released = true
        var failure: Throwable? = null
        try { creation?.cancel() } catch (error: Throwable) { failure = error }
        try { read?.release() } catch (error: Throwable) { failure = failure.addCleanupFailure(error) }
        try { softDelete?.cancel() } catch (error: Throwable) { failure = failure.addCleanupFailure(error) }
        creation = null
        read = null
        softDelete = null
        failure?.let { throw it }
    }
}

/** Own a bounded window, including the first window while schema checks run. */
internal class PreparedGetWindow<DM : IsRootDataModel> private constructor(private val keys: List<Key<DM>>) {
    private val records = arrayOfNulls<PreparedGetRecord>(keys.size)
    private var released = false

    /** Used to reject a missing single key before starting its deferred full read. */
    fun peek(key: Key<DM>): PreparedGetRecord? {
        check(!released)
        val index = keys.indexOf(key)
        return if (index < 0) null else records[index]
    }

    fun take(key: Key<DM>): PreparedGetRecord? = take(keys.indexOf(key))

    fun take(index: Int): PreparedGetRecord? {
        check(!released)
        if (index < 0) return null
        return records[index].also { records[index] = null }
    }

    fun release() {
        if (released) return
        released = true
        var failure: Throwable? = null
        for (index in records.indices) {
            try { records[index]?.release() } catch (error: Throwable) { failure = failure.addCleanupFailure(error) }
            records[index] = null
        }
        failure?.let { throw it }
    }

    companion object {
        fun <DM : IsRootDataModel> prepare(keys: List<Key<DM>>, prepare: (Key<DM>) -> PreparedGetRecord): PreparedGetWindow<DM> {
            val window = PreparedGetWindow(keys)
            try {
                for (index in keys.indices) window.records[index] = prepare(keys[index])
                return window
            } catch (error: Throwable) {
                try { window.release() } catch (cleanup: Throwable) { error.addCleanupFailure(cleanup) }
                throw error
            }
        }
    }
}

private fun Throwable?.addCleanupFailure(error: Throwable): Throwable =
    if (this == null) error else also { if (error !== this) addSuppressed(error) }

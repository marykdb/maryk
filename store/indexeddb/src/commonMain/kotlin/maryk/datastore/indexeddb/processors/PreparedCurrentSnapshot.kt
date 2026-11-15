package maryk.datastore.indexeddb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.ValuesWithMetaData
import maryk.datastore.indexeddb.decodeStorageRowsToValues
import maryk.datastore.indexeddb.decryptSelectedStorageRows

internal const val PREPARED_SNAPSHOT_MIN_BYTES = 64 * 1024

/** Retain selected owned storage rows instead of a large unselected snapshot body. */
internal class PreparedCurrentSnapshot(
    bytes: ByteArray?,
    selected: DecodedIndexedDbSnapshot? = null,
) {
    var bytes = bytes
        private set
    var selected = selected
        private set
    private var released = false

    fun checkOpen() = check(!released) { "Prepared snapshot has been released" }

    fun release() {
        released = true
        bytes = null
        selected = null
    }

    companion object {
        fun prepare(model: IsRootDataModel, bytes: ByteArray?, select: RootPropRefGraph<*>?): PreparedCurrentSnapshot =
            if (bytes != null && bytes.size >= PREPARED_SNAPSHOT_MIN_BYTES && select != null) {
                prepareNarrowSnapshot(model, bytes, select)?.let { PreparedCurrentSnapshot(null, it) }
                    ?: PreparedCurrentSnapshot(bytes)
            } else PreparedCurrentSnapshot(bytes)
    }
}

internal suspend fun <DM : IsRootDataModel> decodeCurrentSnapshotRecord(
    dataModel: DM,
    keyBytes: ByteArray,
    snapshot: PreparedCurrentSnapshot,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray, ByteArray) -> ByteArray,
): ValuesWithMetaData<DM>? {
    snapshot.checkOpen()
    val selected = snapshot.selected ?: return snapshot.bytes?.let {
        decodeCurrentSnapshotRecord(dataModel, keyBytes, it, select, decryptValue = decryptValue)
    }
    return decodeCurrentSnapshotRecord(dataModel, keyBytes, selected, select, decryptValue)
}

internal suspend fun <DM : IsRootDataModel> decodeCurrentSnapshotRecord(
    dataModel: DM,
    keyBytes: ByteArray,
    selected: DecodedIndexedDbSnapshot,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray, ByteArray) -> ByteArray,
): ValuesWithMetaData<DM>? {
    // Preserve the original row count: an empty projection is not an empty legacy snapshot.
    if (selected.meta.isDeleted && selected.storedRowCount == 0) return null
    val values = decodeStorageRowsToValues(dataModel,
        decryptSelectedStorageRows(dataModel, selected.rows, null, decryptValue), select) ?: return null
    return ValuesWithMetaData(dataModel.key(keyBytes), values, selected.meta.firstVersion,
        selected.meta.lastVersion, selected.meta.isDeleted)
}

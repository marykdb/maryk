package maryk.datastore.indexeddb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.datastore.indexeddb.IndexedDbRecordMeta
import maryk.datastore.indexeddb.IndexedDbWriteOperation
import maryk.datastore.shared.rethrowIfFatal



internal fun <DM : IsRootDataModel> encodeVersionedChange(
    dataModel: DM,
    change: DataObjectVersionedChange<DM>,
): ByteArray {
    val requestContext = DataObjectVersionedChange.Serializer.transformContext(requestContextFor(dataModel))
    val cache = WriteCache()
    val byteLength = DataObjectVersionedChange.Serializer.calculateObjectProtoBufLength(change, cache, requestContext)
    val bytes = ByteArray(byteLength)
    var index = 0
    DataObjectVersionedChange.Serializer.writeObjectProtoBuf(change, cache, { bytes[index++] = it }, requestContext)
    return bytes
}

internal fun <DM : IsRootDataModel> decodeVersionedChange(
    dataModel: DM,
    bytes: ByteArray,
): DataObjectVersionedChange<DM> {
    val requestContext = DataObjectVersionedChange.Serializer.transformContext(requestContextFor(dataModel))
    var index = 0
    @Suppress("UNCHECKED_CAST")
    return DataObjectVersionedChange.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, requestContext).toDataObject()
        as DataObjectVersionedChange<DM>
}

internal fun requestContextFor(dataModel: IsRootDataModel): RequestContext =
    RequestContext(
        DefinitionsContext(
            mutableMapOf(dataModel.Meta.name to DataModelReference(dataModel))
        ),
        dataModel = dataModel,
    )

internal fun MutableList<IndexedDbWriteOperation>.put(
    storeName: String,
    key: ByteArray,
    value: ByteArray,
) {
    add(IndexedDbWriteOperation.Put(storeName, key, value))
}

internal fun MutableList<IndexedDbWriteOperation>.delete(
    storeName: String,
    key: ByteArray,
) {
    add(IndexedDbWriteOperation.Delete(storeName, key))
}

internal fun <DM : IsRootDataModel> MutableList<IndexedDbWriteOperation>.addChangeLog(
    dataModel: DM,
    changeStoreName: String,
    keyBytes: ByteArray,
    version: ULong,
    changes: List<IsChange>,
): ByteArray? {
    if (changes.isEmpty()) return null

    val versionedChange = DataObjectVersionedChange(
        key = dataModel.key(keyBytes),
        changes = listOf(
            VersionedChanges(
                version = version,
                changes = changes,
            )
        )
    )

    val encoded = try {
        encodeVersionedChange(dataModel, versionedChange)
    } catch (e: Throwable) {
        e.rethrowIfFatal()
        unserializableChangeLogMarker
    }

    put(
        storeName = changeStoreName,
        key = createChangeLogRowKey(keyBytes, version),
        value = encoded,
    )
    return encoded
}

internal fun MutableList<IndexedDbWriteOperation>.addHistoricSnapshot(
    storeName: String,
    keyBytes: ByteArray,
    version: ULong,
    meta: IndexedDbRecordMeta,
    rows: List<Pair<ByteArray, ByteArray>>,
) {
    put(storeName, createHistoricSnapshotRowKey(keyBytes, version), encodeHistoricSnapshot(meta, rows))
}

internal fun MutableList<IndexedDbWriteOperation>.addHistoricIndexRows(
    storeName: String,
    cleanupStoreName: String,
    keyBytes: ByteArray,
    indexRows: List<ByteArray>,
    version: ULong,
    active: Boolean,
) {
    val value = byteArrayOf(if (active) 1 else 0)
    for (row in indexRows) {
        val historicRowKey = createHistoricVersionedRowKey(row, version)
        put(storeName, historicRowKey, value)
        put(cleanupStoreName, createHistoricCleanupRowKey(keyBytes, historicRowKey), historicRowKey)
    }
}

internal fun MutableList<IndexedDbWriteOperation>.addHistoricUniqueRows(
    storeName: String,
    cleanupStoreName: String,
    uniqueRows: List<Triple<ByteArray, ByteArray, ByteArray>>,
    version: ULong,
    active: Boolean,
) {
    for ((uniqueKey, keyBytes, _) in uniqueRows) {
        val historicRowKey = createHistoricVersionedRowKey(uniqueKey, version)
        put(storeName, historicRowKey, if (active) keyBytes else byteArrayOf())
        put(cleanupStoreName, createHistoricCleanupRowKey(keyBytes, historicRowKey), historicRowKey)
    }
}


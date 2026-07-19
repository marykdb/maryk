package maryk.datastore.indexeddb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.delete
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.IndexedDbRecordMeta
import maryk.datastore.indexeddb.IndexedDbTransactionMode
import maryk.datastore.indexeddb.IndexedDbWriteOperation
import maryk.datastore.indexeddb.decodeRecordMeta
import maryk.datastore.indexeddb.tableQualifierFromRowKey
import maryk.datastore.shared.updates.Update

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processDeleteRequest(
    version: HLC,
    storeAction: DeleteStoreAction<DM>,
) {
    val request = storeAction.request
    val modelId = getDataModelId(request.dataModel)
    val keyStoreName = "k:$modelId"
    val tableStoreName = "t:$modelId"
    val indexStoreName = "i:$modelId"
    val uniqueStoreName = "u:$modelId"
    val changeStoreName = "c:$modelId"
    val updateHistoryStoreName = "uh:$modelId"
    val historicTableStoreName = "ht:$modelId"
    val historicIndexStoreName = "hi:$modelId"
    val historicUniqueStoreName = "hu:$modelId"
    val historicIndexCleanupStoreName = "hik:$modelId"
    val historicUniqueCleanupStoreName = "huk:$modelId"
    val statuses = ArrayList<IsDeleteResponseStatus<DM>>(request.keys.size.coerceAtLeast(4))
    val writeStoreNames = modelWriteStoreNames(
        keyStoreName = keyStoreName,
        tableStoreName = tableStoreName,
        indexStoreName = indexStoreName,
        uniqueStoreName = uniqueStoreName,
        changeStoreName = changeStoreName,
        updateHistoryStoreName = updateHistoryStoreName,
        historicTableStoreName = historicTableStoreName,
        historicIndexStoreName = historicIndexStoreName,
        historicUniqueStoreName = historicUniqueStoreName,
        historicIndexCleanupStoreName = historicIndexCleanupStoreName,
        historicUniqueCleanupStoreName = historicUniqueCleanupStoreName,
    )

    for (key in request.keys) {
        byteStore.transaction(writeStoreNames, IndexedDbTransactionMode.READWRITE) { byteStore ->
            val currentMeta = byteStore.get(keyStoreName, key.bytes)?.let(::decodeRecordMeta)
            if (currentMeta == null) {
                statuses += DoesNotExist(key)
                return@transaction
            }

            val oldTableRows = if (keepAllVersions && !request.hardDelete) {
                scanTableRows(tableStoreName, key.bytes)
            } else {
                emptyList()
            }
            val oldIndexRows = collectCurrentIndexRows(request.dataModel, key.bytes)
            val oldUniqueRows = collectCurrentUniqueRows(request.dataModel, modelId, tableStoreName, key.bytes)
            val operations = mutableListOf<IndexedDbWriteOperation>()
            for (indexRow in oldIndexRows) {
                operations.delete(indexStoreName, indexRow)
            }
            for (row in oldUniqueRows) {
                for (candidateKey in row.candidateKeys) {
                    if (byteStore.get(uniqueStoreName, candidateKey)?.contentEquals(row.keyBytes) == true) {
                        operations.delete(uniqueStoreName, candidateKey)
                    }
                }
            }

            if (request.hardDelete) {
                operations.delete(keyStoreName, key.bytes)
                for ((rowKey, _) in scanTableRows(tableStoreName, key.bytes)) {
                    operations.delete(tableStoreName, rowKey)
                }
                if (keepAllVersions) {
                    for ((rowKey, _) in byteStore.scanObjectScopedRows(historicTableStoreName, key.bytes)) {
                        operations.delete(historicTableStoreName, rowKey)
                    }
                    for ((cleanupRowKey, historicRowKey) in byteStore.historicCleanupRowsForKey(historicIndexCleanupStoreName, key.bytes)) {
                        operations.delete(historicIndexStoreName, historicRowKey)
                        operations.delete(historicIndexCleanupStoreName, cleanupRowKey)
                    }
                    for ((cleanupRowKey, historicRowKey) in byteStore.historicCleanupRowsForKey(historicUniqueCleanupStoreName, key.bytes)) {
                        operations.delete(historicUniqueStoreName, historicRowKey)
                        operations.delete(historicUniqueCleanupStoreName, cleanupRowKey)
                    }
                }
                for ((rowKey, _) in byteStore.scanObjectScopedRows(changeStoreName, key.bytes)) {
                    operations.delete(changeStoreName, rowKey)
                }
                if (keepUpdateHistoryIndex) {
                    operations.put(updateHistoryStoreName, createUpdateHistoryRowKey(version.timestamp, key.bytes), byteArrayOf(1))
                }
            } else {
                operations.put(
                    keyStoreName,
                    key.bytes,
                    encodeCurrentSnapshot(
                        IndexedDbRecordMeta(currentMeta.firstVersion, version.timestamp, true),
                        oldTableRows.map { (rowKey, rowValue) -> tableQualifierFromRowKey(rowKey, key.bytes) to rowValue },
                    )
                )
                if (keepAllVersions) {
                    operations.addHistoricSnapshot(
                        historicTableStoreName,
                        key.bytes,
                        version.timestamp,
                        IndexedDbRecordMeta(currentMeta.firstVersion, version.timestamp, true),
                        oldTableRows.map { (rowKey, rowValue) -> tableQualifierFromRowKey(rowKey, key.bytes) to rowValue },
                    )
                    operations.addHistoricIndexRows(historicIndexStoreName, historicIndexCleanupStoreName, key.bytes, oldIndexRows, version.timestamp, active = true)
                    operations.addHistoricUniqueRows(historicUniqueStoreName, historicUniqueCleanupStoreName, oldUniqueRows, version.timestamp, active = true)
                }
                val changePayload = operations.addChangeLog(
                    dataModel = request.dataModel,
                    changeStoreName = changeStoreName,
                    keyBytes = key.bytes,
                    version = version.timestamp,
                    changes = listOf(ObjectSoftDeleteChange(true))
                )
                if (keepUpdateHistoryIndex && changePayload != null) {
                    operations.put(updateHistoryStoreName, createUpdateHistoryRowKey(version.timestamp, key.bytes), changePayload)
                }
            }

            byteStore.writeBatch(operations)
            statuses += DeleteSuccess(version.timestamp)
            updateSharedFlow.emit(
                Update.Deletion(request.dataModel, key, version.timestamp, request.hardDelete)
            )
        }
    }

    storeAction.response.complete(
        DeleteResponse(
            dataModel = request.dataModel,
            statuses = statuses
        )
    )
}

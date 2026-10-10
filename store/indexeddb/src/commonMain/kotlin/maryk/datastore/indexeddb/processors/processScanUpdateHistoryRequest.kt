package maryk.datastore.indexeddb.processors

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.changes.ObjectCreate
import maryk.core.values.Values
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.scanInBatches
import maryk.datastore.shared.UpdateHistoryPaging

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processScanUpdateHistoryRequest(
    storeAction: ScanUpdateHistoryStoreAction<DM>,
) {
    val request = storeAction.request
    val paging = UpdateHistoryPaging(request)
    if (!(keepAllVersions && keepUpdateHistoryIndex)) {
        throw RequestException("Update history queries require keepAllVersions and keepUpdateHistoryIndex")
    }

    val modelId = getDataModelId(request.dataModel)
    val historicTableStoreName = "ht:$modelId"
    val select = request.select
    val updates = mutableListOf<IsUpdateResponse<DM>>()

    suspend fun matchesHistoryFilter(keyBytes: ByteArray, version: ULong, selectedValues: Values<DM>): Boolean {
        val valuesForFilter = if (select != null && request.where != null) {
            readHistoricRecordDecrypted(
                byteStore, request.dataModel, historicTableStoreName, keyBytes, version, select = null,
            )?.values ?: return false
        } else selectedValues
        return valuesMatchFilter(request.dataModel, valuesForFilter, request.where, version)
    }


    byteStore.scanInBatches(
        storeName = "uh:$modelId",
        startKey = paging.continuation?.let { createUpdateHistoryRowKey(it.version, it.key.bytes) }
            ?: paging.toVersion?.let { (ULong.MAX_VALUE - it).toBigEndianBytes() },
        includeStart = paging.continuation == null,
        targetLimit = UInt.MAX_VALUE,
    ) { rowKey, rowValue ->
        val version = rowKey.readInvertedVersion()
        if (rowKey.size != 8 + request.dataModel.Meta.keyByteSize) return@scanInBatches true
        val toVersion = paging.toVersion
        if (version < request.fromVersion) return@scanInBatches false
        if (toVersion != null && version > toVersion) return@scanInBatches true

        val keyBytes = rowKey.copyOfRange(rowKey.size - request.dataModel.Meta.keyByteSize, rowKey.size)
        if (!paging.accepts(version, request.dataModel.key(keyBytes))) return@scanInBatches true
        if (rowValue.isHardDeleteTombstone()) {
            if (request.where == null) {
                updates += RemovalUpdate(
                    key = request.dataModel.key(keyBytes),
                    version = version,
                    reason = HardDelete,
                )
            }
            return@scanInBatches updates.size.toUInt() < paging.scanLimit
        }

        if (rowValue.isUnserializableChangeLogMarker()) {
            val historicRecord = readHistoricRecordDecrypted(byteStore, 
                dataModel = request.dataModel,
                storeName = historicTableStoreName,
                keyBytes = keyBytes,
                toVersion = version,
                select = request.select,
            )
            if (historicRecord?.firstVersion != version) return@scanInBatches true

            if (request.filterSoftDeleted && historicRecord.isDeleted) return@scanInBatches true
            if (!matchesHistoryFilter(keyBytes, version, historicRecord.values)) return@scanInBatches true

            val changes = (listOf(ObjectCreate) + historicRecord.values.toChanges().toList()).mapNotNull { change ->
                if (select == null) change else change.filterWithSelect(select)
            }
            if (changes.isEmpty()) return@scanInBatches true

            updates += AdditionUpdate(
                key = historicRecord.key,
                version = version,
                firstVersion = historicRecord.firstVersion,
                insertionIndex = updates.size,
                isDeleted = historicRecord.isDeleted,
                values = historicRecord.values,
            )
            return@scanInBatches updates.size.toUInt() < paging.scanLimit
        }

        val decoded = decodeVersionedChange(
            request.dataModel,
            sensitiveFields.decryptChangeLogPayloadIfNeeded(modelId, keyBytes, version, rowValue),
        )
        val historicRecord = readHistoricRecordDecrypted(byteStore, 
            dataModel = request.dataModel,
            storeName = historicTableStoreName,
            keyBytes = decoded.key.bytes,
            toVersion = version,
            select = request.select,
        ) ?: return@scanInBatches true
        if (request.filterSoftDeleted && historicRecord.isDeleted) return@scanInBatches true
        if (!matchesHistoryFilter(keyBytes, version, historicRecord.values)) return@scanInBatches true

        val versionedChanges = decoded.changes.firstOrNull() ?: return@scanInBatches true
        val filteredChanges = versionedChanges.changes.mapNotNull { change ->
            if (select == null) change else change.filterWithSelect(select)
        }
        if (filteredChanges.isEmpty()) return@scanInBatches true

        if (filteredChanges.any { it is ObjectCreate }) {
            updates += AdditionUpdate(
                key = decoded.key,
                version = versionedChanges.version,
                firstVersion = historicRecord.firstVersion,
                insertionIndex = updates.size,
                isDeleted = historicRecord.isDeleted,
                values = historicRecord.values,
            )
        } else {
            updates += ChangeUpdate(
                key = decoded.key,
                version = versionedChanges.version,
                index = updates.size,
                changes = filteredChanges,
            )
        }
        updates.size.toUInt() < paging.scanLimit
    }

    storeAction.response.complete(
        paging.response(updates)
    )
}

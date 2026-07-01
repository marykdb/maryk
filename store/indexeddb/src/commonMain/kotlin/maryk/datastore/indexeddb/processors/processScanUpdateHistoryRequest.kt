package maryk.datastore.indexeddb.processors

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.scanInBatches

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processScanUpdateHistoryRequest(
    storeAction: ScanUpdateHistoryStoreAction<DM>,
) {
    val request = storeAction.request
    if (!(keepAllVersions && keepUpdateHistoryIndex)) {
        throw RequestException("Scan update history requires keepAllVersions and keepUpdateHistoryIndex")
    }

    val modelId = getDataModelId(request.dataModel)
    val historicTableStoreName = "ht:$modelId"
    val updates = mutableListOf<IsUpdateResponse<DM>>()

    if (request.limit == 0u) {
        storeAction.response.complete(
            UpdatesResponse(
                dataModel = request.dataModel,
                updates = emptyList(),
                dataFetchType = FetchByUpdateHistoryIndex(),
            )
        )
        return
    }

    byteStore.scanInBatches(
        storeName = "uh:$modelId",
        targetLimit = UInt.MAX_VALUE,
    ) { rowKey, rowValue ->
        val version = rowKey.readInvertedVersion()
        val toVersion = request.toVersion
        if (version < request.fromVersion) return@scanInBatches false
        if (toVersion != null && version > toVersion) return@scanInBatches true

        val keyBytes = rowKey.copyOfRange(rowKey.size - request.dataModel.Meta.keyByteSize, rowKey.size)
        if (rowValue.size == 1 && rowValue[0] == 1.toByte()) {
            if (request.where == null) {
                updates += RemovalUpdate(
                    key = request.dataModel.key(keyBytes),
                    version = version,
                    reason = HardDelete,
                )
            }
            return@scanInBatches updates.size.toUInt() < request.limit
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
            if (!valuesMatchFilter(request.dataModel, historicRecord.values, request.where, version)) return@scanInBatches true

            val changes = (listOf(ObjectCreate) + historicRecord.values.toChanges().toList()).mapNotNull { change ->
                request.select?.let { change.filterWithSelect(it) } ?: change
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
            return@scanInBatches updates.size.toUInt() < request.limit
        }

        val decoded = decodeVersionedChange(request.dataModel, rowValue)
        val historicRecord = readHistoricRecordDecrypted(byteStore, 
            dataModel = request.dataModel,
            storeName = historicTableStoreName,
            keyBytes = decoded.key.bytes,
            toVersion = version,
            select = request.select,
        ) ?: return@scanInBatches true
        if (request.filterSoftDeleted && historicRecord.isDeleted) return@scanInBatches true
        if (!valuesMatchFilter(request.dataModel, historicRecord.values, request.where, version)) return@scanInBatches true

        val versionedChanges = decoded.changes.firstOrNull() ?: return@scanInBatches true
        val filteredChanges = versionedChanges.changes.mapNotNull { change ->
            request.select?.let { change.filterWithSelect(it) } ?: change
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
        updates.size.toUInt() < request.limit
    }

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = request.dataModel,
            updates = updates,
            dataFetchType = FetchByUpdateHistoryIndex(),
        )
    )
}


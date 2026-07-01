package maryk.datastore.indexeddb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.shared.checkToVersion

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processGetRequest(
    storeAction: GetStoreAction<DM>,
) {
    val request = storeAction.request
    request.checkToVersion(keepAllVersions)

    val modelId = getDataModelId(request.dataModel)
    val keyStoreName = "k:$modelId"
    val tableStoreName = "t:$modelId"
    val historicTableStoreName = "ht:$modelId"
    val aggregator = request.aggregations?.let(::Aggregator)
    val values = ArrayList<ValuesWithMetaData<DM>>(request.keys.size.coerceAtLeast(4))

    for (key in request.keys) {
        val toVersion = request.toVersion
        val record = if (toVersion != null) {
            readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, key.bytes, toVersion, request.select)
        } else {
            readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, key.bytes, request.select)
                ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, key.bytes, request.select)
        }
            ?: continue
        if (request.filterSoftDeleted && record.isDeleted) continue
        val valuesForFilter = if (request.where != null && request.select != null) {
            if (toVersion != null) {
                readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, key.bytes, toVersion, null)
            } else {
                readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, key.bytes, null)
                    ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, key.bytes, null)
            }?.values ?: continue
        } else {
            record.values
        }
        if (!valuesMatchFilter(request.dataModel, valuesForFilter, request.where, request.toVersion)) continue

        values += record
        aggregator?.aggregate { reference -> record.values[reference] }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = request.dataModel,
            values = values,
            aggregations = aggregator?.toResponse(),
            dataFetchType = FetchByKey,
        )
    )
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processGetChangesRequest(
    storeAction: GetChangesStoreAction<DM>,
) {
    val request = storeAction.request
    request.checkToVersion(keepAllVersions)
    if (!keepAllVersions && request.maxVersions > 1u) {
        throw RequestException("Cannot use maxVersions > 1 on a table which has keepAllVersions set to false")
    }

    val modelId = getDataModelId(request.dataModel)
    val keyStoreName = "k:$modelId"
    val changeStoreName = "c:$modelId"
    val historicTableStoreName = "ht:$modelId"
    val changes = mutableListOf<DataObjectVersionedChange<DM>>()

    for (key in request.keys) {
        val versionedChanges = byteStore.readChangeLog(
            dataModel = request.dataModel,
            changeStoreName = changeStoreName,
            historicTableStoreName = historicTableStoreName,
            keyBytes = key.bytes,
            fromVersion = request.fromVersion,
            toVersion = request.toVersion,
            maxVersions = request.maxVersions,
            select = request.select,
            decryptValue = sensitiveFields::decryptValueIfNeeded,
        )

        if (versionedChanges.isEmpty()) continue

        val record = request.toVersion?.let { toVersion ->
            readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, key.bytes, toVersion, null)
        } ?: (
            readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, key.bytes, null)
                ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, "t:$modelId", key.bytes, null)
            )
            ?: continue
        if (request.filterSoftDeleted && record.isDeleted) continue
        if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) continue

        changes += DataObjectVersionedChange(
            key = key,
            changes = versionedChanges,
        )
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = request.dataModel,
            changes = changes,
            dataFetchType = FetchByKey,
        )
    )
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processGetUpdatesRequest(
    storeAction: GetUpdatesStoreAction<DM>,
) {
    val request = storeAction.request
    request.checkToVersion(keepAllVersions)
    if (!keepAllVersions && request.maxVersions > 1u) {
        throw RequestException("Cannot use maxVersions > 1 on a table which has keepAllVersions set to false")
    }

    val modelId = getDataModelId(request.dataModel)
    val keyStoreName = "k:$modelId"
    val tableStoreName = "t:$modelId"
    val historicTableStoreName = "ht:$modelId"
    val updates = mutableListOf<IsUpdateResponse<DM>>()
    val keys = mutableListOf<Key<DM>>()
    var highestVersion = 0uL

    for (key in request.keys) {
        val toVersion = request.toVersion
        val record = if (toVersion != null) {
            readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, key.bytes, toVersion, request.select)
        } else {
            readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, key.bytes, request.select)
                ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, key.bytes, request.select)
        }
            ?: continue
        if (request.filterSoftDeleted && record.isDeleted) continue
        if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) continue

        keys += key
        highestVersion = maxOf(highestVersion, record.lastVersion)
        if (record.lastVersion >= request.fromVersion && (toVersion == null || record.lastVersion <= toVersion)) {
            updates += AdditionUpdate(
                key = key,
                version = record.lastVersion,
                firstVersion = record.firstVersion,
                insertionIndex = keys.lastIndex,
                isDeleted = record.isDeleted,
                values = record.values,
            )
        }
    }

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = request.dataModel,
            updates = listOf(OrderedKeysUpdate(keys, highestVersion)) + updates,
            dataFetchType = FetchByKey,
        )
    )
}


package maryk.datastore.indexeddb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.indexeddb.getBatch
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

    // Filter and project the same record version instead of fetching it twice.
    val readSelect = if (request.where != null && request.select != null) null else request.select
    for (keys in request.keys.chunked(256)) {
        val snapshots = if (request.toVersion == null && keys.size > 1) {
            byteStore.getBatch(keyStoreName, keys.map { it.bytes })
        } else null
        for ((keyIndex, key) in keys.withIndex()) {
            val toVersion = request.toVersion
            val record = if (toVersion != null) {
                readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, key.bytes, toVersion, readSelect)
            } else {
                if (snapshots != null) {
                    val snapshot = snapshots[keyIndex] ?: continue
                    decodeCurrentSnapshotRecord(request.dataModel, key.bytes, snapshot, readSelect) { qualifier, value ->
                        sensitiveFields.decryptValueIfNeeded(modelId, key.bytes, qualifier, value)
                    } ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, key.bytes, readSelect)
                } else {
                    readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, key.bytes, readSelect)
                        ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, key.bytes, readSelect)
                }
            }
                ?: continue
            if (request.filterSoftDeleted && record.isDeleted) continue
            if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) continue

            val selectedRecord = if (readSelect == request.select) record
                else record.copy(values = record.values.filterWithSelect(request.select))
            values += selectedRecord
            aggregator?.aggregate { reference -> selectedRecord.values[reference] }

        }
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
            decryptValue = { qualifier, value -> sensitiveFields.decryptValueIfNeeded(modelId, key.bytes, qualifier, value) },
            decryptChangePayload = { version, value ->
                sensitiveFields.decryptChangeLogPayloadIfNeeded(modelId, key.bytes, version, value)
            },
        )

        val record = request.toVersion?.let { toVersion ->
            readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, key.bytes, toVersion, null)
        } ?: (
            readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, key.bytes, null)
                ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, "t:$modelId", key.bytes, null)
            )
            ?: continue
        if (request.filterSoftDeleted && record.isDeleted) continue
        if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) continue
        if (versionedChanges.isEmpty() && !storeAction.isFlowSnapshotRead) continue

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
    val changeStoreName = "c:$modelId"
    val updates = mutableListOf<IsUpdateResponse<DM>>()
    val keys = mutableListOf<Key<DM>>()
    var highestVersion = 0uL

    for (key in request.keys) {
        val toVersion = request.toVersion
        val record = if (toVersion != null) {
            readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, key.bytes, toVersion, null)
        } else {
            readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, key.bytes, null)
                ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, key.bytes, null)
        }
            ?: continue
        if (request.filterSoftDeleted && record.isDeleted) continue
        if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) continue

        keys += key
        highestVersion = maxOf(highestVersion, record.lastVersion)
        val versionedChanges = byteStore.readChangeLog(
            dataModel = request.dataModel,
            changeStoreName = changeStoreName,
            historicTableStoreName = historicTableStoreName.takeIf { keepAllVersions },
            currentRecord = record.takeUnless { keepAllVersions },
            keyBytes = key.bytes,
            fromVersion = request.fromVersion,
            toVersion = request.toVersion,
            maxVersions = request.maxVersions,
            select = request.select,
            decryptValue = { qualifier, value -> sensitiveFields.decryptValueIfNeeded(modelId, key.bytes, qualifier, value) },
            decryptChangePayload = { version, value ->
                sensitiveFields.decryptChangeLogPayloadIfNeeded(modelId, key.bytes, version, value)
            },
        )

        for (versionedChange in versionedChanges) {
            if (versionedChange.changes.any { it is ObjectCreate }) {
                val historicRecord = if (keepAllVersions) {
                    readHistoricRecordDecrypted(
                        byteStore,
                        request.dataModel,
                        historicTableStoreName,
                        key.bytes,
                        versionedChange.version,
                        request.select,
                    )
                } else {
                    null
                }
                updates += AdditionUpdate(
                    key = key,
                    version = versionedChange.version,
                    firstVersion = historicRecord?.firstVersion ?: versionedChange.version,
                    insertionIndex = keys.lastIndex,
                    isDeleted = historicRecord?.isDeleted ?: false,
                    values = historicRecord?.values ?: request.dataModel.fromChanges(null, versionedChange.changes),
                )
            } else {
                updates += ChangeUpdate(
                    key = key,
                    version = versionedChange.version,
                    index = keys.lastIndex,
                    changes = versionedChange.changes,
                )
            }
        }
    }

    updates.sortBy { it.version }
    highestVersion = minOf(request.toVersion ?: ULong.MAX_VALUE, highestVersion)

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = request.dataModel,
            updates = listOf(OrderedKeysUpdate(keys, highestVersion)) + updates,
            dataFetchType = FetchByKey,
        )
    )
}

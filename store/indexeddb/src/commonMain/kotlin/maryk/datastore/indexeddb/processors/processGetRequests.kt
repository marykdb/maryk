package maryk.datastore.indexeddb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.indexeddb.getBatch
import maryk.datastore.indexeddb.IndexedDbBatchReadStore
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.fromReplicationChanges
import maryk.datastore.shared.normalizeReplicationChanges
import maryk.datastore.shared.RequestRecordCache

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processGetRequest(
    storeAction: GetStoreAction<DM>,
    prefetchedSnapshots: List<ByteArray?>? = null,
    preparedSnapshots: MutableMap<Int, DecodedIndexedDbSnapshot>? = null,
): Boolean {
    val request = storeAction.request
    request.checkToVersion(keepAllVersions)

    val modelId = getDataModelId(request.dataModel)
    val keyStoreName = "k:$modelId"
    val tableStoreName = "t:$modelId"
    val historicTableStoreName = "ht:$modelId"
    val aggregator = request.aggregations?.let(::Aggregator)
    val values = ArrayList<ValuesWithMetaData<DM>>(request.keys.size.coerceAtLeast(4))
    // Referenced filters fetch other records outside the co-read native snapshot.
    val hasPrefetchedSnapshots = prefetchedSnapshots != null || preparedSnapshots != null
    var usedOnlyPrefetchedSnapshots = hasPrefetchedSnapshots && request.where?.hasReferencedQualifier() != true

    // Filter and project the same record version instead of fetching it twice.
    val readSelect = request.dataModel.requiredReadSelection(request.select, request.where, request.aggregations)
    // Only co-read native heads prove a shared view. Legacy row fallbacks and
    // filters retain their original paths, including schema validation.
    var records = if (hasPrefetchedSnapshots && request.where == null) {
        if (request.toVersion == null && request.aggregations == null && request.select == null) {
            RequestRecordCache.create(request.keys, maxValueNodes = 16_384)
        } else RequestRecordCache.create(request.keys)
    } else null
    for (keys in request.keys.asSequence().chunked(256)) {
        var selectedRows = preparedSnapshots
        val snapshots = prefetchedSnapshots ?: if (preparedSnapshots == null && request.toVersion == null && keys.size > 1) {
            val batchStore = byteStore
            if (readSelect != null && isNarrowSnapshotSelection(request.dataModel, readSelect) && batchStore is IndexedDbBatchReadStore) {
                batchStore.mapBatchValues(keyStoreName, keys.map { it.bytes }) { index, bytes ->
                    if (bytes != null && bytes.size >= PREPARED_SNAPSHOT_MIN_BYTES) {
                        val selected = prepareNarrowSnapshot(request.dataModel, bytes, readSelect)
                        if (selected == null) bytes else {
                            val rows = selectedRows ?: HashMap<Int, DecodedIndexedDbSnapshot>().also { selectedRows = it }
                            rows[index] = selected
                            null
                        }
                    } else bytes
                }
            } else batchStore.getBatch(keyStoreName, keys.map { it.bytes })
        } else null
        for ((keyIndex, key) in keys.withIndex()) {
            val toVersion = request.toVersion
            val prepared = selectedRows?.remove(keyIndex)
            val record = (if (records?.contains(key) == true) records.get(key) else (if (toVersion != null) {
                readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, key.bytes, toVersion, readSelect)
            } else {
                if (prepared != null) {
                    val snapshot = prepared
                    decodeCurrentSnapshotRecord(request.dataModel, key.bytes, snapshot, readSelect) { qualifier, value ->
                        sensitiveFields.decryptValueIfNeeded(modelId, key.bytes, qualifier, value)
                    } ?: run {
                        usedOnlyPrefetchedSnapshots = false
                        records = null
                        readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, key.bytes, readSelect)
                    }
                } else if (snapshots != null) {
                    val snapshot = snapshots[keyIndex] ?: continue
                    decodeCurrentSnapshotRecord(request.dataModel, key.bytes, snapshot, readSelect) { qualifier, value ->
                        sensitiveFields.decryptValueIfNeeded(modelId, key.bytes, qualifier, value)
                    } ?: run {
                        usedOnlyPrefetchedSnapshots = false
                        records = null
                        readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, key.bytes, readSelect)
                    }
                } else {
                    readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, key.bytes, readSelect)
                        ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, key.bytes, readSelect)
                }
            }).also { records?.put(key, it) })
                ?: continue
            if (request.filterSoftDeleted && record.isDeleted) continue
            if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) continue

            val selectedRecord = if (readSelect == request.select) record
                else record.copy(values = record.values.filterWithSelect(request.select))
            values += selectedRecord
            aggregator?.aggregate { reference -> record.values[reference] }

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
    return usedOnlyPrefetchedSnapshots
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
                    isDeleted = historicRecord?.isDeleted
                        ?: (versionedChange.changes.filterIsInstance<ObjectSoftDeleteChange>().lastOrNull()?.isDeleted ?: false),
                    values = historicRecord?.values ?: request.dataModel.fromReplicationChanges(
                        null, normalizeReplicationChanges(versionedChange.changes),
                    ),
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

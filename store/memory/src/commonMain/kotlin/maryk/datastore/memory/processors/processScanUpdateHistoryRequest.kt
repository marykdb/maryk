package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.IsUpdateHistoryRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataStore.UpdateHistoryRecord
import maryk.lib.extensions.compare.compareTo
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.shared.UpdateHistoryPaging
import maryk.datastore.shared.StoreAction

internal typealias ScanUpdateHistoryStoreAction<DM> = StoreAction<DM, IsUpdateHistoryRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdateHistoryStoreAction = ScanUpdateHistoryStoreAction<IsRootDataModel>

internal fun <DM : IsRootDataModel> processScanUpdateHistoryRequest(
    storeAction: ScanUpdateHistoryStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<DM>
) {
    val scanRequest = storeAction.request
    val paging = UpdateHistoryPaging(scanRequest)
    val dataStore = dataStoreFetcher.invoke(scanRequest.dataModel)

    if (!dataStore.keepAllVersions || !dataStore.keepUpdateHistoryIndex) {
        throw updateHistoryNotAvailable()
    }

    val expectedSize = scanRequest.limit.toInt().coerceAtLeast(4)
    val updates = ArrayList<IsUpdateResponse<DM>>(expectedSize + 1)

    val orderedHistory = dataStore.updateHistory.sortedWith(
        compareByDescending<UpdateHistoryRecord> { it.version }
            .thenComparator { left, right -> left.keyBytes compareTo right.keyBytes }
    )
    for (entry in orderedHistory) {
        if (entry.version > (paging.toVersion ?: ULong.MAX_VALUE)) continue
        if (entry.version < scanRequest.fromVersion) break
        if (updates.size.toUInt() >= paging.scanLimit) break

        val key = Key<DM>(entry.keyBytes)
        if (!paging.accepts(entry.version, key)) continue
        val version = HLC(entry.version)
        val recordFetcher = createStoreRecordFetcher(dataStoreFetcher, version)

        @Suppress("UNCHECKED_CAST")
        val record = recordFetcher(scanRequest.dataModel, key) as DataRecord<DM>?
        if (record == null) {
            val preDeleteVersion = entry.version.takeIf { it > 0uL }?.let { HLC(it - 1uL) }
            val shouldEmitHardDelete = if (entry.isHardDelete) {
                when {
                    scanRequest.where == null -> true
                    preDeleteVersion == null -> false
                    else -> {
                        val preDeleteRecordFetcher = createStoreRecordFetcher(dataStoreFetcher, preDeleteVersion)
                        @Suppress("UNCHECKED_CAST")
                        val preDeleteRecord = preDeleteRecordFetcher(scanRequest.dataModel, key) as DataRecord<DM>?
                        preDeleteRecord != null &&
                            !scanRequest.shouldBeFiltered(preDeleteRecord, preDeleteVersion, preDeleteRecordFetcher)
                    }
                }
            } else {
                false
            }

            if (shouldEmitHardDelete) {
                updates += RemovalUpdate(
                    key = key,
                    version = entry.version,
                    reason = HardDelete
                )
            }
            continue
        }

        if (scanRequest.shouldBeFiltered(record, version, recordFetcher)) continue

        fun addCreation(creationVersion: ULong) {
            scanRequest.dataModel.recordToValueWithMeta(
                scanRequest.select,
                HLC(creationVersion),
                record
            )?.let { valuesWithMeta ->
                updates += AdditionUpdate(
                    key = key,
                    version = creationVersion,
                    firstVersion = valuesWithMeta.firstVersion,
                    insertionIndex = updates.size,
                    isDeleted = valuesWithMeta.isDeleted,
                    values = valuesWithMeta.values
                )
            }
        }

        // Creation is an event even when the selected projection has no properties.
        if (record.firstVersion.timestamp == entry.version) {
            addCreation(entry.version)
            continue
        }

        scanRequest.dataModel.recordToObjectChanges(
            scanRequest.select,
            entry.version,
            entry.version,
            1u,
            null,
            record
        )?.changes?.forEach { versionedChange ->
            val changes = versionedChange.changes
            if (changes.contains(ObjectCreate)) {
                addCreation(versionedChange.version)
            } else {
                updates += ChangeUpdate(
                    key = key,
                    version = versionedChange.version,
                    index = updates.size,
                    changes = changes
                )
            }
        }
    }

    storeAction.response.complete(
        paging.response(updates)
    )
}

private fun updateHistoryNotAvailable() =
    RequestException("Update history queries require keepAllVersions and a ready update history index")

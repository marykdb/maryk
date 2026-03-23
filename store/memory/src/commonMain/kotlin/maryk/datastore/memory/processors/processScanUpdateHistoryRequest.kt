package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.ScanUpdateHistoryRequest
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.shared.StoreAction

internal typealias ScanUpdateHistoryStoreAction<DM> = StoreAction<DM, ScanUpdateHistoryRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdateHistoryStoreAction = ScanUpdateHistoryStoreAction<IsRootDataModel>

internal fun <DM : IsRootDataModel> processScanUpdateHistoryRequest(
    storeAction: ScanUpdateHistoryStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<DM>
) {
    val scanRequest = storeAction.request
    val dataStore = dataStoreFetcher.invoke(scanRequest.dataModel)

    if (!dataStore.keepAllVersions || !dataStore.keepUpdateHistoryIndex) {
        throw updateHistoryNotAvailable()
    }

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher)
    val updates = mutableListOf<IsUpdateResponse<DM>>()

    for (entry in dataStore.updateHistory) {
        if (entry.version > (scanRequest.toVersion ?: ULong.MAX_VALUE)) continue
        if (entry.version < scanRequest.fromVersion) break
        if (updates.size.toUInt() >= scanRequest.limit) break

        val key = Key<DM>(entry.keyBytes)

        @Suppress("UNCHECKED_CAST")
        val record = recordFetcher(scanRequest.dataModel, key) as DataRecord<DM>?
        if (record == null) {
            if (entry.isHardDelete && scanRequest.where == null) {
                updates += RemovalUpdate(
                    key = key,
                    version = entry.version,
                    reason = HardDelete
                )
            }
            continue
        }

        val version = HLC(entry.version)
        if (scanRequest.shouldBeFiltered(record, version, recordFetcher)) continue

        scanRequest.dataModel.recordToObjectChanges(
            scanRequest.select,
            entry.version,
            entry.version + 1uL,
            1u,
            null,
            record
        )?.changes?.mapNotNull { versionedChange ->
            val changes = versionedChange.changes
            if (changes.contains(ObjectCreate)) {
                scanRequest.dataModel.recordToValueWithMeta(
                    scanRequest.select,
                    HLC(versionedChange.version),
                    record
                )?.let { valuesWithMeta ->
                    AdditionUpdate(
                        key = key,
                        version = versionedChange.version,
                        firstVersion = valuesWithMeta.firstVersion,
                        insertionIndex = updates.size,
                        isDeleted = valuesWithMeta.isDeleted,
                        values = valuesWithMeta.values
                    )
                }
            } else {
                ChangeUpdate(
                    key = key,
                    version = versionedChange.version,
                    index = updates.size,
                    changes = changes
                )
            }
        }?.let { updates += it }
    }

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = scanRequest.dataModel,
            updates = updates,
            dataFetchType = FetchByUpdateHistoryIndex(),
        )
    )
}

private fun updateHistoryNotAvailable() =
    RequestException("scanUpdateHistory requires keepAllVersions and a ready update history index")

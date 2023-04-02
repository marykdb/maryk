package maryk.datastore.memory.processors

import maryk.core.models.IsRootDataModel
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions

internal typealias ScanChangesStoreAction<DM> = StoreAction<DM, ScanChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootDataModel>

/** Processes a ScanChangesRequest in a [storeAction] into a dataStore from [dataStoreFetcher] */
internal fun <DM : IsRootDataModel> processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<*>
) {
    val scanRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher)

    @Suppress("UNCHECKED_CAST")
    val dataStore = (dataStoreFetcher as IsStoreFetcher<DM>).invoke(scanRequest.dataModel)

    scanRequest.checkMaxVersions(dataStore.keepAllVersions)

    processScan(scanRequest, dataStore, recordFetcher) { record, sortingKey ->
        scanRequest.dataModel.recordToObjectChanges(
            scanRequest.select,
            scanRequest.fromVersion,
            scanRequest.toVersion,
            scanRequest.maxVersions,
            sortingKey,
            record
        )?.let {
            // Only add if not null
            objectChanges += it
        }
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = scanRequest.dataModel,
            changes = objectChanges
        )
    )
}

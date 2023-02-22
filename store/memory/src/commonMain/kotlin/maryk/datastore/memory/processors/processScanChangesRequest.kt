package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions

internal typealias ScanChangesStoreAction<DM, P> = StoreAction<DM, P, ScanChangesRequest<DM, P>, ChangesResponse<DM, P>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootValuesDataModel<IsValuesPropertyDefinitions>, IsValuesPropertyDefinitions>

/** Processes a ScanChangesRequest in a [storeAction] into a dataStore from [dataStoreFetcher] */
internal fun <DM : IsRootValuesDataModel<P>, P : IsValuesPropertyDefinitions> processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM, P>,
    dataStoreFetcher: IsStoreFetcher<*, *>
) {
    val scanRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher)

    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(scanRequest.dataModel) as DataStore<DM, P>

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

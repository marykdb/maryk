package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion

internal typealias GetChangesStoreAction<DM, P> = StoreAction<DM, P, GetChangesRequest<DM, P>, ChangesResponse<DM>>
internal typealias AnyGetChangesStoreAction = GetChangesStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a GetChangesRequest in a [storeAction] into a dataStore from [dataStoreFetcher] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processGetChangesRequest(
    storeAction: GetChangesStoreAction<DM, P>,
    dataStoreFetcher: IsStoreFetcher<*, *>
) {
    val getRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()

    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(getRequest.dataModel) as DataStore<DM, P>

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher)

    getRequest.checkToVersion(dataStore.keepAllVersions)

    for (key in getRequest.keys) {
        val index = dataStore.records.binarySearch { it.key.compareTo(key) }

        // Only return if found
        if (index > -1) {
            val record = dataStore.records[index]

            if (getRequest.shouldBeFiltered(record, getRequest.toVersion?.let { HLC(it) }, recordFetcher)) {
                continue
            }

            getRequest.dataModel.recordToObjectChanges(
                getRequest.select,
                getRequest.fromVersion,
                getRequest.toVersion,
                record
            )?.let {
                // Only add if not null
                objectChanges += it
            }
        }
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = getRequest.dataModel,
            changes = objectChanges
        )
    )
}

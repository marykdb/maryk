package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.memory.StoreAction
import maryk.datastore.memory.records.DataStore

internal typealias GetStoreAction<DM, P> = StoreAction<DM, P, GetRequest<DM, P>, ValuesResponse<DM, P>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a GetRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processGetRequest(
    storeAction: GetStoreAction<DM, P>,
    dataStore: DataStore<DM, P>
) {
    val getRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM, P>>()

    for (key in getRequest.keys) {
        val index = dataStore.records.binarySearch { it.key.compareTo(key) }

        // Only return if found
        if (index > -1) {
            val record = dataStore.records[index]

            if (getRequest.filterData(record, getRequest.toVersion)) {
                continue
            }

            getRequest.dataModel.recordToValueWithMeta(
                getRequest.select,
                getRequest.toVersion,
                record
            )?.let {
                // Only add if not null
                valuesWithMeta += it
            }
        }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = getRequest.dataModel,
            values = valuesWithMeta
        )
    )
}

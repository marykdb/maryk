package maryk.datastore.memory.processors

import maryk.core.aggregations.Aggregator
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
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

    val aggregator = getRequest.aggregations?.let {
        Aggregator(it)
    }

    for (key in getRequest.keys) {
        val index = dataStore.records.binarySearch { it.key.compareTo(key) }

        // Only return if found
        if (index > -1) {
            val record = dataStore.records[index]
            val toVersion = getRequest.toVersion?.let { HLC(it) }

            if (getRequest.shouldBeFiltered(record, toVersion)) {
                continue
            }

            val valuesWithMetaData = getRequest.dataModel.recordToValueWithMeta(
                getRequest.select,
                toVersion,
                record
            )?.also {
                // Only add if not null
                valuesWithMeta += it
            }

            aggregator?.aggregate {
                @Suppress("UNCHECKED_CAST")
                valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                    ?: record[it, toVersion]
            }
        }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = getRequest.dataModel,
            values = valuesWithMeta,
            aggregations = aggregator?.toResponse()
        )
    )
}

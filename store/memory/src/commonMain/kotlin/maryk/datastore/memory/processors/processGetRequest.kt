package maryk.datastore.memory.processors

import maryk.core.aggregations.Aggregator
import maryk.core.clock.HLC
import maryk.core.properties.IsRootModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion

internal typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootModel>

/** Processes a GetRequest in a [storeAction] and resolve dataStore with [dataStoreFetcher] */
internal fun <DM : IsRootModel> processGetRequest(
    storeAction: GetStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<*>
) {
    val getRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM>>()
    val toVersion = getRequest.toVersion?.let { HLC(it) }

    @Suppress("UNCHECKED_CAST")
    val dataStore = (dataStoreFetcher as IsStoreFetcher<DM>).invoke(getRequest.dataModel)

    getRequest.checkToVersion(dataStore.keepAllVersions)

    val aggregator = getRequest.aggregations?.let {
        Aggregator(it)
    }

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher)

    for (key in getRequest.keys) {
        val index = dataStore.records.binarySearch { it.key compareTo key }

        // Only return if found
        if (index > -1) {
            val record = dataStore.records[index]
            if (getRequest.shouldBeFiltered(record, toVersion, recordFetcher)) {
                continue
            }

            val valuesWithMetaData = getRequest.dataModel.recordToValueWithMeta(
                getRequest.select,
                toVersion,
                record
            )?.also {
                // Only add if not null
                valuesWithMeta.add(it)
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

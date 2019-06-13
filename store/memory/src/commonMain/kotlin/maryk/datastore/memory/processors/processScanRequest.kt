package maryk.datastore.memory.processors

import maryk.core.aggregations.Aggregator
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ScanRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.shared.StoreAction
import maryk.datastore.memory.records.DataStore

internal typealias ScanStoreAction<DM, P> = StoreAction<DM, P, ScanRequest<DM, P>, ValuesResponse<DM, P>>
internal typealias AnyScanStoreAction = ScanStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ScanRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processScanRequest(
    storeAction: ScanStoreAction<DM, P>,
    dataStore: DataStore<DM, P>
) {
    val scanRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM, P>>()

    val aggregator = scanRequest.aggregations?.let {
        Aggregator(it)
    }

    processScan(scanRequest, dataStore) { record ->
        val toVersion = scanRequest.toVersion?.let { HLC(it) }

        val valuesWithMetaData = scanRequest.dataModel.recordToValueWithMeta(
            scanRequest.select,
            toVersion,
            record
        )?.also {
            valuesWithMeta += it
        }

        // Aggregate all values. First try from valuesWithMetaData and otherwise directly from record
        aggregator?.aggregate {
            @Suppress("UNCHECKED_CAST")
            valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                ?: record[it, toVersion]
        }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = scanRequest.dataModel,
            values = valuesWithMeta,
            aggregations = aggregator?.toResponse()
        )
    )
}

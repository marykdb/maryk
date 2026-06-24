package maryk.datastore.memory.processors

import maryk.core.aggregations.Aggregator
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ScanRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction

internal typealias ScanStoreAction<DM> = StoreAction<DM, ScanRequest<DM>, ValuesResponse<DM>>
internal typealias AnyScanStoreAction = ScanStoreAction<IsRootDataModel>

/** Processes a ScanRequest in a [storeAction] into a dataStore from [dataStoreFetcher] */
internal fun <DM : IsRootDataModel> processScanRequest(
    storeAction: ScanStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<DM>
) {
    val scanRequest = storeAction.request
    val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(scanRequest.limit.toInt().coerceAtLeast(4))

    val aggregator = scanRequest.aggregations?.let {
        Aggregator(it)
    }

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher, scanRequest.toVersion?.let(::HLC))

    val dataStore = dataStoreFetcher.invoke(scanRequest.dataModel)

    val dataFetchType = processScan(
        scanRequest = scanRequest,
        dataStore = dataStore,
        recordFetcher = recordFetcher,
        allowTableScanOverride = scanRequest.toVersion != null
    ) { record, _ ->
        val toVersion = scanRequest.toVersion?.let { HLC(it) }

        val valuesWithMetaData = scanRequest.dataModel.recordToValueWithMeta(
            scanRequest.select,
            toVersion,
            record
        )?.also { valuesWithMeta.add(it) }

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
            aggregations = aggregator?.toResponse(),
            dataFetchType = dataFetchType,
        )
    )
}

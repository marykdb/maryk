package maryk.datastore.memory.processors

import maryk.core.aggregations.Aggregator
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.createCursor
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
    val dataStore = dataStoreFetcher.invoke(scanRequest.dataModel)
    // A complete current table scan has a useful bound from the store itself.
    // Bound speculative space even when many current records are soft-deleted.
    // Filtered, historical and paged scans keep the small initial allocation.
    val capacity = if (scanRequest.where == null && scanRequest.toVersion == null &&
        scanRequest.order == null && scanRequest.startKey == null && scanRequest.cursor == null
    ) minOf(scanRequest.limit, dataStore.records.size.toUInt(), 16_384u).toInt().coerceAtLeast(4)
        else scanRequest.limit.coerceIn(4u, 256u).toInt()
    val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(capacity)

    val aggregator = scanRequest.aggregations?.let {
        Aggregator(it)
    }

    val toVersion = scanRequest.toVersion?.let(::HLC)
    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher, toVersion)
    val projection = scanRequest.select?.let {
        ScanProjection(scanRequest.dataModel, it)
    }

    var lastEmittedKey: Key<DM>? = null
    var lastEmittedOrderKey: ByteArray? = null

    val dataFetchType = processScan(
        scanRequest = scanRequest,
        dataStore = dataStore,
        recordFetcher = recordFetcher,
        allowTableScanOverride = scanRequest.toVersion != null
    ) { record, orderKey ->
        val valuesWithMetaData = scanRequest.dataModel.recordToValueWithMeta(
            scanRequest.select,
            toVersion,
            record,
            projection?.qualifiers(record.values.size),
        )
        if (valuesWithMeta.size.toUInt() < scanRequest.limit && valuesWithMetaData != null) {
            valuesWithMeta.add(valuesWithMetaData)
            lastEmittedKey = record.key
            lastEmittedOrderKey = orderKey
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
            aggregations = aggregator?.toResponse(),
            dataFetchType = dataFetchType,
            nextCursor = lastEmittedKey
                ?.takeIf { valuesWithMeta.size.toUInt() == scanRequest.limit }
                ?.let { scanRequest.createCursor(it, lastEmittedOrderKey) },
        )
    )
}

/** Prepare once, only after encountering a record large enough to benefit from seeking. */

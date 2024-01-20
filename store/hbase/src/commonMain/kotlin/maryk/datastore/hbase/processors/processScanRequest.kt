package maryk.datastore.hbase.processors

import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ScanRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion

internal typealias ScanStoreAction<DM> = StoreAction<DM, ScanRequest<DM>, ValuesResponse<DM>>
internal typealias AnyScanStoreAction = ScanStoreAction<IsRootDataModel>

/** Processes a ScanRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processScanRequest(
    storeAction: ScanStoreAction<DM>,
    dataStore: HbaseDataStore,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM>>()
    val dbIndex = dataStore.getDataModelId(scanRequest.dataModel)

    val aggregator = scanRequest.aggregations?.let {
        Aggregator(it)
    }

    scanRequest.checkToVersion(dataStore.keepAllVersions)

    val table = dataStore.getTable(scanRequest.dataModel)

    processScan(
        table,
        scanRequest,
        dataStore,
    ) { key, creationVersion, result, _ ->
        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
            cache.readValue(dbIndex, key, reference, version, valueReader)
        }

        val valuesWithMetaData = scanRequest.dataModel.readResultIntoValuesWithMetaData(
            result,
            creationVersion!!,
            key,
            scanRequest.select,
            cacheReader
        )?.also {
            // Only add if not null
            valuesWithMeta.add(it)
        }

        aggregator?.aggregate {
            @Suppress("UNCHECKED_CAST")
            valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                ?: result.getColumnLatestCell(dataColumnFamily, it.toStorageByteArray()).let { valueCell ->
                    (it.propertyDefinition as IsStorageBytesEncodable<Any>).fromStorageBytes(valueCell.valueArray, valueCell.valueOffset, valueCell.valueLength)
                }
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

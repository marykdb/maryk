package maryk.datastore.rocksdb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ScanRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.rocksdb.use

internal typealias ScanStoreAction<DM> = StoreAction<DM, ScanRequest<DM>, ValuesResponse<DM>>
internal typealias AnyScanStoreAction = ScanStoreAction<IsRootDataModel>

/** Processes a ScanRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootDataModel> processScanRequest(
    storeAction: ScanStoreAction<DM>,
    dataStore: RocksDBDataStore,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM>>()
    val dbIndex = dataStore.getDataModelId(scanRequest.dataModel)
    val columnFamilies = dataStore.getColumnFamilies(dbIndex)

    val aggregator = scanRequest.aggregations?.let {
        Aggregator(it)
    }

    DBAccessor(dataStore).use { transaction ->
        val columnToScan = if (scanRequest.toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = transaction.getIterator(dataStore.defaultReadOptions, columnToScan)

        processScan(
            scanRequest,
            dataStore,
            transaction,
            columnFamilies,
            dataStore.defaultReadOptions
        ) { key, creationVersion, _ ->
            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                cache.readValue(dbIndex, key, reference, version, valueReader)
            }

            val valuesWithMetaData = scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                iterator,
                creationVersion,
                columnFamilies,
                key,
                scanRequest.select,
                scanRequest.toVersion,
                cacheReader
            )?.also {
                // Only add if not null
                valuesWithMeta.add(it)
            }

            aggregator?.aggregate {
                @Suppress("UNCHECKED_CAST")
                valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                    ?: transaction.getValue(
                        columnFamilies,
                        dataStore.defaultReadOptions,
                        scanRequest.toVersion,
                        it.toStorageByteArray()
                    ) { valueBytes, offset, length ->
                        (it.propertyDefinition as IsStorageBytesEncodable<Any>).fromStorageBytes(
                            valueBytes,
                            offset,
                            length
                        )
                    }
            }
        }

        iterator.close()
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = scanRequest.dataModel,
            values = valuesWithMeta,
            aggregations = aggregator?.toResponse()
        )
    )
}

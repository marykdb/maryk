package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.runBlocking
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

internal typealias ScanStoreAction<DM> = StoreAction<DM, ScanRequest<DM>, ValuesResponse<DM>>
internal typealias AnyScanStoreAction = ScanStoreAction<IsRootDataModel>

/** Processes a ScanRequest in a [storeAction] into a [RocksDBDataStore] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processScanRequest(
    storeAction: ScanStoreAction<DM>,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM>>()
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)

    val aggregator = scanRequest.aggregations?.let {
        Aggregator(it)
    }

    DBAccessor(this).use { transaction ->
        val columnToScan = if (scanRequest.toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = transaction.getIterator(defaultReadOptions, columnToScan)

        val dataFetchType = processScan(
            scanRequest,
            transaction,
            columnFamilies,
            defaultReadOptions
        ) { key, creationVersion, _ ->
            val cacheReader =
                { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                    runBlocking {
                        cache.readValue(dbIndex, key, reference, version, valueReader)
                    }
                }

            val valuesWithMetaData = scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                iterator,
                creationVersion,
                columnFamilies,
                key,
                scanRequest.select,
                scanRequest.toVersion,
                cacheReader
            )?.let { values ->
                if (scanRequest.toVersion == null) {
                    values
                } else {
                    val deleted = isSoftDeleted(
                        transaction,
                        columnFamilies,
                        defaultReadOptions,
                        scanRequest.toVersion,
                        key.bytes,
                        0,
                        key.size
                    )
                    if (values.isDeleted == deleted) values else values.copy(isDeleted = deleted)
                }
            }?.also {
                // Only add if not null
                valuesWithMeta.add(it)
            }

            aggregator?.aggregate {
                @Suppress("UNCHECKED_CAST")
                valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                    ?: transaction.getValue(
                        columnFamilies,
                        defaultReadOptions,
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

        storeAction.response.complete(
            ValuesResponse(
                dataModel = scanRequest.dataModel,
                values = valuesWithMeta,
                aggregations = aggregator?.toResponse(),
                dataFetchType = dataFetchType,
            )
        )
    }
}

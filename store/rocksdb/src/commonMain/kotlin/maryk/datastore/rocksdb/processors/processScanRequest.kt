package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.createCursor
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.HistoricalTableReader
import maryk.datastore.rocksdb.processors.helpers.RequestKeySoftDeleteCache
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.helpers.convertToValue

internal typealias ScanStoreAction<DM> = StoreAction<DM, ScanRequest<DM>, ValuesResponse<DM>>
internal typealias AnyScanStoreAction = ScanStoreAction<IsRootDataModel>

/** Processes a ScanRequest in a [storeAction] into a [RocksDBDataStore] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processScanRequest(
    storeAction: ScanStoreAction<DM>,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(scanRequest.limit.toInt().coerceAtLeast(4))
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)

    val aggregator = scanRequest.aggregations?.let {
        Aggregator(it)
    }
    var lastEmittedKey: Key<DM>? = null
    var lastEmittedOrderKey: ByteArray? = null

    DBAccessor(this).use { transaction ->
        val columnToScan = if (scanRequest.toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val historicalReader = scanRequest.toVersion?.let { toVersion ->
            (columnFamilies as? HistoricTableColumnFamilies)?.let {
                HistoricalTableReader(transaction, it, sequentialReadOptions, toVersion)
            }
        }
        val softDeleteCache = RequestKeySoftDeleteCache(
            transaction,
            columnFamilies,
            defaultReadOptions,
            scanRequest.toVersion,
            historicalReader
        )
        transaction.getIterator(sequentialReadOptions, columnToScan).use { iterator ->
            historicalReader.use { reader ->
                val dataFetchType = processScan(
                    scanRequest,
                    transaction,
                    columnFamilies,
                    defaultReadOptions,
                    includeSortingKey = true,
                    softDeleteCache = softDeleteCache,
                    historicalReader = reader
                ) { key, creationVersion, orderKey ->
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
                            val deleted = softDeleteCache.get(key.bytes, 0, key.size)
                            if (values.isDeleted == deleted) values else values.copy(isDeleted = deleted)
                        }
                    }
                    if (valuesWithMeta.size.toUInt() < scanRequest.limit && valuesWithMetaData != null) {
                        valuesWithMeta.add(valuesWithMetaData)
                        lastEmittedKey = key
                        lastEmittedOrderKey = orderKey
                    }

                    aggregator?.aggregate {
                        @Suppress("UNCHECKED_CAST")
                        valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                            ?: transaction.getValue(
                                columnFamilies,
                                defaultReadOptions,
                                scanRequest.toVersion,
                                it.toStorageByteArray(key.bytes),
                                reader
                            ) { valueBytes, offset, length ->
                                valueBytes.convertToValue(it, offset, length)
                            }
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
        }
    }
}

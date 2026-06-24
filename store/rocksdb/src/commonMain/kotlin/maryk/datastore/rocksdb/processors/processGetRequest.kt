package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.HistoricalTableReader
import maryk.datastore.rocksdb.processors.helpers.RequestKeySoftDeleteCache
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.helpers.convertToValue

internal typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootDataModel>

/** Processes a GetRequest in a [storeAction] into a [RocksDBDataStore] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processGetRequest(
    storeAction: GetStoreAction<DM>,
    cache: Cache
) {
    val getRequest = storeAction.request
    val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(getRequest.keys.size.coerceAtLeast(4))
    val dbIndex = getDataModelId(getRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)

    val aggregator = getRequest.aggregations?.let {
        Aggregator(it)
    }

    getRequest.checkToVersion(keepAllVersions)

    DBAccessor(this).use { dbAccessor ->
        val columnToScan = if (getRequest.toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val historicalReader = getRequest.toVersion?.let { toVersion ->
            (columnFamilies as? HistoricTableColumnFamilies)?.let {
                HistoricalTableReader(dbAccessor, it, sequentialReadOptions, toVersion)
            }
        }
        val softDeleteCache = RequestKeySoftDeleteCache(
            dbAccessor,
            columnFamilies,
            defaultReadOptions,
            getRequest.toVersion,
            historicalReader
        )
        dbAccessor.getIterator(sequentialReadOptions, columnToScan).use { iterator ->
            historicalReader.use { reader ->
                keyWalk@ for (key in getRequest.keys) {
                    readCreationVersion(
                        dbAccessor,
                        columnFamilies,
                        defaultReadOptions,
                        key.bytes,
                        getRequest.toVersion
                    )?.let { creationVersion ->
                        val deleted = if (getRequest.toVersion != null || getRequest.filterSoftDeleted) {
                            softDeleteCache.get(key.bytes, 0, key.size)
                        } else {
                            null
                        }
                        if (
                            getRequest.shouldBeFiltered(
                                dbAccessor,
                                columnFamilies,
                                defaultReadOptions,
                                key.bytes,
                                0,
                                key.size,
                                creationVersion,
                                getRequest.toVersion,
                                historicalReader = reader,
                                softDeleteCache = softDeleteCache
                            )
                        ) {
                            continue@keyWalk
                        }

                        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                            runBlocking {
                                cache.readValue(dbIndex, key, reference, version, valueReader)
                            }
                        }

                        val valuesWithMetaData = getRequest.dataModel.readTransactionIntoValuesWithMetaData(
                            iterator,
                            creationVersion,
                            columnFamilies,
                            key,
                            getRequest.select,
                            getRequest.toVersion,
                            cacheReader
                        )?.let { values ->
                            if (getRequest.toVersion == null) {
                                values
                            } else {
                                val cachedDeleted = deleted ?: softDeleteCache.get(key.bytes, 0, key.size)
                                if (values.isDeleted == cachedDeleted) values else values.copy(isDeleted = cachedDeleted)
                            }
                        }?.also {
                            valuesWithMeta.add(it)
                        }

                        aggregator?.aggregate {
                            @Suppress("UNCHECKED_CAST")
                            valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                                ?: dbAccessor.getValue(
                                    columnFamilies,
                                    defaultReadOptions,
                                    getRequest.toVersion,
                                    it.toStorageByteArray(key.bytes),
                                    reader
                                ) { valueBytes, offset, length ->
                                    valueBytes.convertToValue(it, offset, length)
                                }
                        }
                    }
                }
            }
        }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = getRequest.dataModel,
            values = valuesWithMeta,
            aggregations = aggregator?.toResponse(),
            dataFetchType = FetchByKey,
        )
    )
}

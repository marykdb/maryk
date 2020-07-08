package maryk.datastore.rocksdb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion
import maryk.lib.recyclableByteArray
import maryk.rocksdb.rocksDBNotFound
import maryk.rocksdb.use

internal typealias GetStoreAction<DM, P> = StoreAction<DM, P, GetRequest<DM, P>, ValuesResponse<DM, P>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a GetRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processGetRequest(
    storeAction: GetStoreAction<DM, P>,
    dataStore: RocksDBDataStore,
    cache: Cache
) {
    val getRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM, P>>()
    val dbIndex = dataStore.getDataModelId(getRequest.dataModel)
    val columnFamilies = dataStore.getColumnFamilies(dbIndex)

    val aggregator = getRequest.aggregations?.let {
        Aggregator(it)
    }

    getRequest.checkToVersion(dataStore.keepAllVersions)

    DBAccessor(dataStore).use { dbAccessor ->
        val columnToScan = if (getRequest.toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = dbAccessor.getIterator(dataStore.defaultReadOptions, columnToScan)

        keyWalk@ for (key in getRequest.keys) {
            val mayExist = dataStore.db.keyMayExist(columnFamilies.keys, key.bytes, null)
            if (mayExist) {
                val valueLength =
                    dbAccessor.get(columnFamilies.keys, dataStore.defaultReadOptions, key.bytes, recyclableByteArray)

                if (valueLength != rocksDBNotFound) {
                    val creationVersion = recyclableByteArray.readVersionBytes()
                    if (
                        getRequest.shouldBeFiltered(dbAccessor, columnFamilies, dataStore.defaultReadOptions, key.bytes, 0, key.size, creationVersion, getRequest.toVersion)
                    ) {
                        continue@keyWalk
                    }

                    val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                        cache.readValue(dbIndex, key, reference, version, valueReader)
                    }

                    val valuesWithMetaData = getRequest.dataModel.readTransactionIntoValuesWithMetaData(
                        iterator,
                        creationVersion,
                        columnFamilies,
                        key,
                        getRequest.select,
                        getRequest.toVersion,
                        cacheReader
                    )?.also {
                        // Only add if not null
                        valuesWithMeta.add(it)
                    }

                    aggregator?.aggregate {
                        @Suppress("UNCHECKED_CAST")
                        valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                            ?: dbAccessor.getValue(
                                columnFamilies,
                                dataStore.defaultReadOptions,
                                getRequest.toVersion,
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
            }
        }

        iterator.close()
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = getRequest.dataModel,
            values = valuesWithMeta,
            aggregations = aggregator?.toResponse()
        )
    )
}

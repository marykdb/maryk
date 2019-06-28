package maryk.datastore.rocksdb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.extensions.bytes.toULong
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.WriteOptions
import maryk.rocksdb.use

internal typealias GetStoreAction<DM, P> = StoreAction<DM, P, GetRequest<DM, P>, ValuesResponse<DM, P>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a GetRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processGetRequest(
    storeAction: GetStoreAction<DM, P>,
    dataStore: RocksDBDataStore
) {
    val getRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM, P>>()
    val columnFamilies = dataStore.getColumnFamilies(storeAction.dbIndex)

    val aggregator = getRequest.aggregations?.let {
        Aggregator(it)
    }

    getRequest.checkToVersion(dataStore.keepAllVersions)

    WriteOptions().use { writeOptions ->
        dataStore.db.beginTransaction(writeOptions).use { transaction ->
            ReadOptions().use { readOptions ->
                // On iteration, dont iterate past the prefix/key
                readOptions.setPrefixSameAsStart(true)
                keyWalk@ for (key in getRequest.keys) {
                    val mayExist = dataStore.db.keyMayExist(columnFamilies.table, key.bytes, StringBuilder())
                    if (mayExist) {
                        val creationVersion = transaction.get(columnFamilies.table, readOptions, key.bytes)?.toULong()

                        if (creationVersion != null) {
                            if (getRequest.shouldBeFiltered(transaction, columnFamilies, readOptions, key, creationVersion, getRequest.toVersion)) {
                                continue@keyWalk
                            }

                            val valuesWithMetaData = getRequest.dataModel.readTransactionIntoValuesWithMetaData(
                                transaction,
                                readOptions,
                                creationVersion,
                                columnFamilies,
                                key,
                                getRequest.select,
                                getRequest.toVersion
                            )?.also {
                                // Only add if not null
                                valuesWithMeta += it
                            }

                            aggregator?.aggregate {
                                @Suppress("UNCHECKED_CAST")
                                valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                                  ?: transaction.getValue(columnFamilies, readOptions, creationVersion, it.toStorageByteArray()) { valueBytes, offset, length ->
                                      (it.propertyDefinition as IsStorageBytesEncodable<Any>).fromStorageBytes(valueBytes, offset, length)
                                  }
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
            aggregations = aggregator?.toResponse()
        )
    )
}

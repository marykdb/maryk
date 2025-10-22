package maryk.datastore.foundationdb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion

internal typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootDataModel>

internal fun <DM : IsRootDataModel> FoundationDBDataStore.processGetRequest(
    storeAction: GetStoreAction<DM>,
    cache: Cache,
) {
    val getRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM>>()
    val dbIndex = getDataModelId(getRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    val aggregator = getRequest.aggregations?.let { Aggregator(it) }

    getRequest.checkToVersion(keepAllVersions)

    keyWalk@ for (key in getRequest.keys) {
        val valuesWithMetaData = runTransaction { tr ->
            val existing = tr.get(packKey(tableDirs.keysPrefix, key.bytes)).awaitResult()
            if (existing == null) {
                null
            } else {
                val creationVersion = HLC.fromStorageBytes(existing, 0).timestamp

                if (getRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, key.size, creationVersion, getRequest.toVersion)) {
                    null
                } else {
                    val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                        cache.readValue(dbIndex, key, reference, version, valueReader)
                    }

                    getRequest.dataModel.readTransactionIntoValuesWithMetaData(
                        tr = tr,
                        creationVersion = creationVersion,
                        tableDirs = tableDirs,
                        key = key,
                        select = getRequest.select,
                        toVersion = getRequest.toVersion,
                        cachedRead = cacheReader
                    )
                }
            }
        }

        if (valuesWithMetaData == null) continue@keyWalk
        valuesWithMeta.add(valuesWithMetaData)

        aggregator?.aggregate {
            @Suppress("UNCHECKED_CAST")
            valuesWithMetaData.values[it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>]
                ?: runTransaction { tr ->
                    tr.getValue(
                        tableDirs = tableDirs,
                        toVersion = getRequest.toVersion,
                        keyAndReference = it.toStorageByteArray()
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

    storeAction.response.complete(
        ValuesResponse(
            dataModel = getRequest.dataModel,
            values = valuesWithMeta,
            aggregations = aggregator?.toResponse(),
            dataFetchType = FetchByKey,
        )
    )
}

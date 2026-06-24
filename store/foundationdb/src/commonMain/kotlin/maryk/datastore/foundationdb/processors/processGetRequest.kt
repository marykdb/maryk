package maryk.datastore.foundationdb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.readCreationVersion
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.helpers.convertToValue

internal typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootDataModel>

internal fun <DM : IsRootDataModel> FoundationDBDataStore.processGetRequest(
    storeAction: GetStoreAction<DM>,
    cache: Cache,
) {
    val getRequest = storeAction.request
    val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(getRequest.keys.size.coerceAtLeast(4))
    val dbIndex = getDataModelId(getRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    val aggregator = getRequest.aggregations?.let { Aggregator(it) }

    getRequest.checkToVersion(keepAllVersions)

    runTransaction { tr ->
        keyWalk@ for (key in getRequest.keys) {
            val keyBytes = key.bytes
            val valuesWithMetaData = run {
                val creationVersion = tr.readCreationVersion(tableDirs, keyBytes, getRequest.toVersion)
                    ?: return@run null

                if (getRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, key.size, creationVersion, getRequest.toVersion, this@processGetRequest::decryptValueIfNeeded)) {
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
                        cachedRead = cacheReader,
                        decryptValue = this@processGetRequest::decryptValueIfNeeded
                    )
                }
            }

            if (valuesWithMetaData == null) continue@keyWalk
            valuesWithMeta.add(valuesWithMetaData)

            aggregator?.aggregate {
                @Suppress("UNCHECKED_CAST")
                valuesWithMetaData.values[it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>]
                    ?: tr.getValue(
                            tableDirs = tableDirs,
                            toVersion = getRequest.toVersion,
                            keyBytes = keyBytes,
                            referenceBytes = it.toStorageByteArray(),
                            decryptValue = this@processGetRequest::decryptValueIfNeeded
                        ) { valueBytes, offset, length ->
                            valueBytes.convertToValue(it, offset, length)
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
}

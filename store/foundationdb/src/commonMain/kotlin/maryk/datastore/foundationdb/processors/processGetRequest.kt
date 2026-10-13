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
import maryk.datastore.foundationdb.FoundationDBReadContext
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.readCreationVersion
import maryk.datastore.foundationdb.processors.helpers.prepareRecordRead
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfExact
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.helpers.convertToValue

internal typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootDataModel>

internal fun <DM : IsRootDataModel> FoundationDBDataStore.processGetRequest(
    storeAction: GetStoreAction<DM>,
    cache: Cache,
    readContext: FoundationDBReadContext,
) {
    val getRequest = storeAction.request
    val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(getRequest.keys.size.coerceAtLeast(4))
    val dbIndex = getDataModelId(getRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    val aggregator = getRequest.aggregations?.let(::Aggregator)

    getRequest.checkToVersion(keepAllVersions)

    runReadTransaction(readContext, dbIndex) { tr ->
        // Limit outstanding row reads so wide records cannot multiply memory without bound.
        for (keys in getRequest.keys.chunked(8)) {
            val batchCurrent = getRequest.toVersion == null && keys.size > 1
            val creations = if (batchCurrent) keys.map { tr.get(packKey(tableDirs.keysPrefix, it.bytes)) } else null
            val reads = if (batchCurrent && getRequest.select?.properties?.isEmpty() != true) keys.map {
                getRequest.dataModel.prepareRecordRead(tr, tableDirs, it.bytes, getRequest.select, null)
            } else null
            try {
                keyWalk@ for ((keyIndex, key) in keys.withIndex()) {
                    val keyBytes = key.bytes
                    val valuesWithMetaData = run {
                        val creationVersion = (if (creations != null) {
                            creations[keyIndex].awaitResult()?.readHLCTimestampIfExact()
                        } else tr.readCreationVersion(tableDirs, keyBytes, getRequest.toVersion)) ?: return@run null

                        if (getRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, key.size, creationVersion, getRequest.toVersion, { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) })) {
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
                                decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) },
                                preparedRead = reads?.get(keyIndex),
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
                                    decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }
                                ) { valueBytes, offset, length ->
                                    valueBytes.convertToValue(it, offset, length)
                                }
                    }
                }
            } finally {
                reads?.forEach { it.cancel() }
                creations?.forEach { it.cancel() }
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

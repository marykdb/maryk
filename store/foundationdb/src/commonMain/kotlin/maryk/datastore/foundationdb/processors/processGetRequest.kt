package maryk.datastore.foundationdb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
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
import maryk.datastore.foundationdb.processors.helpers.PreparedRecordRead
import maryk.datastore.shared.RequestRecordCache
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.helpers.convertToValue

internal typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootDataModel>

internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processGetRequest(
    storeAction: GetStoreAction<DM>,
    cache: Cache,
    readContext: FoundationDBReadContext,
) {
    val getRequest = storeAction.request
    val dbIndex = getDataModelId(getRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    getRequest.checkToVersion(keepAllVersions)

    runReadTransactionSuspending(readContext, dbIndex) { tr ->
        val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(getRequest.keys.size.coerceAtLeast(4))
        val aggregator = getRequest.aggregations?.let(::Aggregator)
        val records = RequestRecordCache.create(getRequest.keys)
        val memoizedBytes = records?.let { HashMap<Key<DM>, Long>() }
        // Limit outstanding row reads so wide records cannot multiply memory without bound.
        for (offset in getRequest.keys.indices step 8) {
            val keys = getRequest.keys.subList(offset, minOf(offset + 8, getRequest.keys.size))
            val preparedKeys = if (records == null) keys else keys.distinct().filterNot(records::contains)
            val consumed = if (records == null) null else HashSet<Key<DM>>()
            val creations = if (getRequest.toVersion == null) preparedKeys.map { tr.get(packKey(tableDirs.keysPrefix, it.bytes)) } else null
            val reads = if (getRequest.select?.properties?.isEmpty() != true) preparedKeys.map {
                getRequest.dataModel.prepareRecordRead(tr, tableDirs, it.bytes, getRequest.select, getRequest.toVersion, readContext.readBudget)
            } else null
            try {
                keyWalk@ for ((originalIndex, key) in keys.withIndex()) {
                    val keyIndex = if (records == null) originalIndex else preparedKeys.indexOf(key)
                    var ownedRead: PreparedRecordRead? = null
                    try {
                        val keyBytes = key.bytes
                        val valuesWithMetaData = if (records?.contains(key) == true) {
                            readContext.readBudget.consumeMemoizedBytes(memoizedBytes?.get(key) ?: 0L)
                            records.get(key)
                        } else run {
                            val usePrepared = consumed?.add(key) != false
                            ownedRead = if (reads == null) null else if (usePrepared) reads[keyIndex] else
                                getRequest.dataModel.prepareRecordRead(tr, tableDirs, keyBytes, getRequest.select, getRequest.toVersion, readContext.readBudget)
                            val creationVersion = (if (creations != null) {
                                (if (usePrepared) creations[keyIndex].await() else tr.get(packKey(tableDirs.keysPrefix, keyBytes)).await())?.readHLCTimestampIfExact()
                            } else tr.readCreationVersion(tableDirs, keyBytes, getRequest.toVersion)) ?: return@run null

                            if (getRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, key.size, creationVersion, getRequest.toVersion, valueDecryptor)) {
                                null
                            } else {
                                val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                                    cache.readValue(dbIndex, key, reference, version, valueReader)
                                }

                                ownedRead?.awaitRowsSuspending()
                                getRequest.dataModel.readTransactionIntoValuesWithMetaData(
                                    tr = tr,
                                    creationVersion = creationVersion,
                                    tableDirs = tableDirs,
                                    key = key,
                                    select = getRequest.select,
                                    toVersion = getRequest.toVersion,
                                    cachedRead = cacheReader,
                                    decryptValue = valueDecryptor,
                                    preparedRead = ownedRead,
                                    readBudget = readContext.readBudget,
                                )
                            }
                        }.also {
                            records?.put(key, it)
                            if (it != null && records?.contains(key) == true) {
                                memoizedBytes?.put(key, ownedRead?.materializedBytes ?: 0L)
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
                                        decryptValue = valueDecryptor
                                    ) { valueBytes, offset, length ->
                                        valueBytes.convertToValue(it, offset, length)
                                    }
                        }
                    } finally {
                        ownedRead?.release()
                        creations?.getOrNull(keyIndex)?.cancel()
                    }
                }
            } finally {
                reads?.forEach { it.release() }
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

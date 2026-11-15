package maryk.datastore.foundationdb.processors

import maryk.foundationdb.FdbFuture
import maryk.datastore.shared.createRecordAggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.FoundationDBReadContext
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.cachedValueReader
import maryk.datastore.foundationdb.processors.helpers.readCreationVersion
import maryk.datastore.foundationdb.processors.helpers.prepareCurrentFullRecordRead
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfExact
import maryk.datastore.foundationdb.processors.helpers.PreparedRecordRead
import maryk.datastore.foundationdb.processors.helpers.GetReadPreparation
import maryk.datastore.foundationdb.processors.helpers.PreparedGetWindow
import maryk.datastore.foundationdb.processors.helpers.GET_READ_WINDOW_SIZE
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

    if (getRequest.keys.size == 1 && getRequest.toVersion == null &&
        getRequest.select == null && getRequest.aggregations == null) {
        processCurrentFullSingleGet(storeAction, cache, readContext, dbIndex, tableDirs)
        return
    }

    val preparation = GetReadPreparation(getRequest, tableDirs, readContext.readBudget)
    val singleKey = preparation.singleCurrentKey
    var earlyWindow: PreparedGetWindow<DM>? = null
    try {
        runReadTransactionSuspending(readContext, dbIndex, prepare = { tr ->
            earlyWindow?.release()
            earlyWindow = preparation.prepareEarly(tr)
        }) { tr ->
            val aggregator = getRequest.aggregations?.let { getRequest.dataModel.createRecordAggregator(it, getRequest.select) }
            // Current projections overlap metadata checks and reuse their deletion marker.
            val singleVersion = singleKey?.let { earlyWindow?.peek(it)?.creation?.await()?.readHLCTimestampIfExact() }
            if (singleKey != null && singleVersion == null) {
                storeAction.response.complete(ValuesResponse(
                    dataModel = getRequest.dataModel, values = emptyList(),
                    aggregations = aggregator?.toResponse(), dataFetchType = FetchByKey,
                ))
                return@runReadTransactionSuspending
            }
            val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(getRequest.keys.size.coerceAtLeast(4))
            val records = if (getRequest.keys.size > 1 && getRequest.toVersion == null &&
                getRequest.where == null && getRequest.aggregations == null && getRequest.select == null
            ) RequestRecordCache.create(getRequest.keys, maxValueNodes = 16_384)
            else RequestRecordCache.create(getRequest.keys)
            val memoizedBytes = records?.let { HashMap<Key<DM>, Long>() }
            // Limit outstanding row reads so wide records cannot multiply memory without bound.
            for (offset in getRequest.keys.indices step GET_READ_WINDOW_SIZE) {
                val keys = getRequest.keys.subList(offset, minOf(offset + GET_READ_WINDOW_SIZE, getRequest.keys.size))
                val preparedKeys = if (records == null) keys else keys.distinct().filterNot(records::contains)
                val window = preparation.prepareWindow(tr, preparedKeys, earlyWindow)
                try {
                    keyWalk@ for ((originalIndex, key) in keys.withIndex()) {
                        val keyBytes = key.bytes
                        val valuesWithMetaData = if (records?.contains(key) == true) {
                            readContext.readBudget.consumeMemoizedBytes(memoizedBytes?.get(key) ?: 0L)
                            records.get(key)
                        } else {
                            val record = (if (records == null) window.take(originalIndex) else window.take(key))
                                ?: preparation.prepareRecord(tr, key)
                            try {
                                val creationVersion = if (singleKey != null) singleVersion else if (record.creation != null) {
                                    record.creation?.await()?.readHLCTimestampIfExact()
                                } else tr.readCreationVersion(tableDirs, keyBytes, getRequest.toVersion)
                                val value = if (creationVersion == null) null else {
                                    val preparedSoftDeleted = if (getRequest.toVersion == null && getRequest.filterSoftDeleted) {
                                        record.currentSoftDeleted(tableDirs, keyBytes, valueDecryptor)
                                    } else null
                                    if (getRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, key.size, creationVersion,
                                            getRequest.toVersion, valueDecryptor, preparedSoftDeleted = preparedSoftDeleted)) {
                                        null
                                    } else {
                                        record.read?.awaitRowsSuspending()
                                        getRequest.dataModel.readTransactionIntoValuesWithMetaData(
                                            tr = tr,
                                            creationVersion = creationVersion,
                                            tableDirs = tableDirs,
                                            key = key,
                                            select = getRequest.select,
                                            toVersion = getRequest.toVersion,
                                            cachedRead = cache.cachedValueReader(dbIndex, key),
                                            decryptValue = valueDecryptor,
                                            preparedRead = record.read,
                                            readBudget = readContext.readBudget,
                                        )
                                    }
                                }
                                records?.put(key, value)
                                if (value != null && records?.contains(key) == true) {
                                    memoizedBytes?.put(key, record.read?.materializedBytes ?: 0L)
                                }
                                value
                            } finally {
                                record.release()
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
                    }
                } finally {
                    window.release()
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
    } finally {
        earlyWindow?.release()
    }
}

/** A full single-record read needs neither a batch window nor duplicate-record bookkeeping. */
private suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processCurrentFullSingleGet(
    storeAction: GetStoreAction<DM>,
    cache: Cache,
    readContext: FoundationDBReadContext,
    dbIndex: UInt,
    tableDirs: IsTableDirectories,
) {
    val request = storeAction.request
    val key = request.keys.single()
    var creation: FdbFuture<ByteArray?>? = null
    try {
        runReadTransactionSuspending(readContext, dbIndex, prepare = { tr ->
            creation?.cancel()
            creation = tr.get(packKey(tableDirs.keysPrefix, key.bytes))
        }) { tr ->
            val version = creation?.await()?.readHLCTimestampIfExact()
            var prepared: PreparedRecordRead? = null
            val record = try {
                if (version == null) null else {
                    prepared = prepareCurrentFullRecordRead(
                        tr, tableDirs, key.bytes, readContext.readBudget, request.filterSoftDeleted,
                    )
                    val softDeleted = if (request.filterSoftDeleted) {
                        isCurrentSoftDeleted(prepared.awaitCurrentSoftDeleteValue(), tableDirs, key.bytes, valueDecryptor)
                    } else null
                    if (request.shouldBeFiltered(tr, tableDirs, key.bytes, 0, key.size, version, null, valueDecryptor,
                            preparedSoftDeleted = softDeleted)) {
                        null
                    } else {
                        prepared.awaitRowsSuspending()
                        request.dataModel.readTransactionIntoValuesWithMetaData(
                            tr = tr,
                            creationVersion = version,
                            tableDirs = tableDirs,
                            key = key,
                            select = null,
                            toVersion = null,
                            cachedRead = cache.cachedValueReader(dbIndex, key),
                            decryptValue = valueDecryptor,
                            preparedRead = prepared,
                            readBudget = readContext.readBudget,
                        )
                    }
                }
            } finally {
                prepared?.release()
            }
            storeAction.response.complete(ValuesResponse(
                dataModel = request.dataModel,
                values = if (record == null) emptyList() else listOf(record),
                dataFetchType = FetchByKey,
            ))
        }
    } finally {
        creation?.cancel()
    }
}

package maryk.datastore.rocksdb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.RocksDBReadContext
import maryk.datastore.rocksdb.processors.helpers.prepareProjectedQualifiers
import maryk.datastore.rocksdb.processors.helpers.HistoricalTableReader
import maryk.datastore.rocksdb.processors.helpers.RequestKeySoftDeleteCache
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.shared.RequestRecordCache
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.helpers.convertToValue

internal typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootDataModel>

/** Processes a GetRequest in a [storeAction] into a [RocksDBDataStore] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processGetRequest(
    storeAction: GetStoreAction<DM>,
    cache: Cache,
    readContext: RocksDBReadContext,
) {
    val defaultReadOptions = readContext.defaultReadOptions
    val getRequest = storeAction.request
    val dbIndex = getDataModelId(getRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)

    val aggregator = getRequest.aggregations?.let {
        Aggregator(it)
    }

    getRequest.checkToVersion(keepAllVersions)

    val valuesWithMeta = readContext.withAccessor(this) { dbAccessor ->
        val singleKey = getRequest.keys.singleOrNull()
        val singleCreation = singleKey?.let {
            readCreationVersion(dbAccessor, columnFamilies, defaultReadOptions, it.bytes, getRequest.toVersion)
        }
        if (singleKey != null && singleCreation == null) return@withAccessor emptyList()
        val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(getRequest.keys.size.coerceAtLeast(4))
        val records = if (getRequest.keys.size > 1 && getRequest.toVersion == null &&
            getRequest.where == null && getRequest.aggregations == null && getRequest.select == null
        ) RequestRecordCache.create(getRequest.keys, maxValueNodes = 16_384)
        else RequestRecordCache.create(getRequest.keys)
        val columnToScan = if (getRequest.toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val historicalReader = getRequest.toVersion?.let { toVersion ->
            (columnFamilies as? HistoricTableColumnFamilies)?.let {
                HistoricalTableReader(dbAccessor, it, readContext.sequentialReadOptions, toVersion)
            }
        }
        val softDeleteCache = RequestKeySoftDeleteCache(
            dbAccessor,
            columnFamilies,
            defaultReadOptions,
            getRequest.toVersion,
            historicalReader
        )
        var projectionPrepared = false
        var projectionQualifiers: List<ByteArray>? = null
        var iterator: DBIterator? = null
        try {
            historicalReader.use { reader ->
                keyWalk@ for (key in getRequest.keys) {
                    val valuesWithMetaData = if (records?.contains(key) == true) {
                        records.get(key) ?: continue@keyWalk
                    } else {
                        val creationVersion = (if (singleKey != null) singleCreation else readCreationVersion(
                            dbAccessor,
                            columnFamilies,
                            defaultReadOptions,
                            key.bytes,
                            getRequest.toVersion
                        )) ?: run { records?.put(key, null); continue@keyWalk }
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
                            records?.put(key, null)
                            continue@keyWalk
                        }

                        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                            cache.readValue(dbIndex, key, reference, version, valueReader)
                        }

                        // Seek directly rather than advancing through unrelated collection cells.
                        val recordIterator = iterator ?: dbAccessor.getIterator(readContext.sequentialReadOptions, columnToScan).also { iterator = it }
                        recordIterator.seek(key.bytes)
                        if (!projectionPrepared) {
                            projectionQualifiers = if (getRequest.toVersion == null) getRequest.dataModel.prepareProjectedQualifiers(getRequest.select) else null
                            projectionPrepared = true
                        }
                        getRequest.dataModel.readTransactionIntoValuesWithMetaDataPrepared(
                            recordIterator,
                            this,
                            creationVersion,
                            columnFamilies,
                            key,
                            getRequest.select,
                            getRequest.toVersion,
                            projectionQualifiers,
                            cacheReader
                        )?.let { values ->
                            if (getRequest.toVersion == null && getRequest.select?.properties?.isEmpty() != true) {
                                values
                            } else {
                                val cachedDeleted = deleted ?: softDeleteCache.get(key.bytes, 0, key.size)
                                if (values.isDeleted == cachedDeleted) values else values.copy(isDeleted = cachedDeleted)
                            }
                        }?.also { records?.put(key, it) }
                    }
                    valuesWithMetaData?.let { valuesWithMeta.add(it) }

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
        } finally {
            iterator?.close()
        }
        valuesWithMeta
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

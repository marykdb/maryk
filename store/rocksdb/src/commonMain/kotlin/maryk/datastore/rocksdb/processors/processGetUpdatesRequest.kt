package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.HistoricalTableReader
import maryk.datastore.rocksdb.processors.helpers.RequestKeySoftDeleteCache
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.rocksdb.processors.helpers.readVersionBytesIfExact
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions

internal typealias GetUpdatesStoreAction<DM> = StoreAction<DM, GetUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyGetUpdatesStoreAction = GetUpdatesStoreAction<IsRootDataModel>

/** Processes a GetUpdatesRequest in a [storeAction] into a [RocksDBDataStore] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processGetUpdatesRequest(
    storeAction: GetUpdatesStoreAction<DM>,
    cache: Cache
) {
    val getRequest = storeAction.request

    getRequest.checkToVersion(keepAllVersions)
    getRequest.checkMaxVersions(keepAllVersions)

    val expectedSize = getRequest.keys.size.coerceAtLeast(4)
    val matchingKeys = ArrayList<Key<DM>>(expectedSize)
    val updates = ArrayList<IsUpdateResponse<DM>>(expectedSize + 1)
    var lastResponseVersion = 0uL
    var insertionIndex = -1

    DBAccessor(this).use { dbAccessor ->
        val dbIndex = this.getDataModelId(getRequest.dataModel)
        val columnFamilies = this.getColumnFamilies(dbIndex)

        val columnToScan = if ((getRequest.toVersion != null || getRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val currentReadIterator = dbAccessor.getIterator(sequentialReadOptions, columnToScan)
        val historicReadIterator = (columnFamilies as? HistoricTableColumnFamilies)?.let {
            if (columnToScan === it.historic.table) null else dbAccessor.getIterator(sequentialReadOptions, it.historic.table)
        }
        currentReadIterator.use { currentIterator ->
            historicReadIterator.use { historicIterator ->
                val historicalReader = getRequest.toVersion?.let { toVersion ->
                    (columnFamilies as? HistoricTableColumnFamilies)?.let {
                        HistoricalTableReader(dbAccessor, it, sequentialReadOptions, toVersion)
                    }
                }
                historicalReader.use { reader ->
                    val softDeleteCache = RequestKeySoftDeleteCache(
                        dbAccessor,
                        columnFamilies,
                        defaultReadOptions,
                        getRequest.toVersion,
                        reader
                    )
                    val objectIterator = if ((getRequest.toVersion != null || getRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
                        historicIterator ?: currentIterator
                    } else {
                        currentIterator
                    }
                    keyWalk@ for (key in getRequest.keys) {
                        readCreationVersion(
                            dbAccessor,
                            columnFamilies,
                            defaultReadOptions,
                            key.bytes,
                            getRequest.toVersion
                        )?.let { creationVersion ->
                                if (getRequest.shouldBeFiltered(
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

                                val latestKey = key.bytes + LAST_VERSION_INDICATOR
                                val latestLength = dbAccessor.get(columnFamilies.table, defaultReadOptions, latestKey, recyclableByteArray)
                                val lastVersion = recyclableByteArray.readVersionBytesIfExact(latestLength)
                                    ?: getRequest.toVersion
                                    ?: continue@keyWalk

                                insertionIndex++

                                matchingKeys.add(key)
                                lastResponseVersion = maxOf(lastResponseVersion, lastVersion)

                                val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                                    runBlocking {
                                        cache.readValue(dbIndex, key, reference, version, valueReader)
                                    }
                                }

                                fun getSingleValues(version: ULong?) =
                                    run {
                                        val readVersion = version.takeIf { columnFamilies is HistoricTableColumnFamilies }
                                        val deepIterator =
                                            if ((version != null || getRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
                                                historicIterator ?: currentIterator
                                            } else {
                                                currentIterator
                                            }
                                        getRequest.dataModel.readTransactionIntoValuesWithMetaData(
                                            deepIterator,
                                            creationVersion,
                                            columnFamilies,
                                            key,
                                            getRequest.select,
                                            readVersion,
                                            cacheReader
                                        )?.withSoftDeleteState(
                                            dbAccessor = dbAccessor,
                                            columnFamilies = columnFamilies,
                                            readOptions = defaultReadOptions,
                                            key = key.bytes,
                                            toVersion = readVersion,
                                            reader = reader.takeIf { readVersion == getRequest.toVersion }
                                        )
                                    }

                                val objectChange = getRequest.dataModel.readTransactionIntoObjectChanges(
                                    objectIterator,
                                    creationVersion,
                                    columnFamilies,
                                    key,
                                    getRequest.select,
                                    getRequest.fromVersion,
                                    getRequest.toVersion,
                                    getRequest.maxVersions,
                                    null,
                                    cacheReader
                                )
                                val updatedObjectChange = if (getRequest.needsSoftDeleteFallback() && columnFamilies is HistoricTableColumnFamilies) {
                                    addSoftDeleteChangeIfMissing(
                                        dbAccessor = dbAccessor,
                                        columnFamilies = columnFamilies,
                                        readOptions = defaultReadOptions,
                                        key = key,
                                        fromVersion = getRequest.fromVersion,
                                        objectChange = objectChange
                                    )
                                } else {
                                    objectChange
                                }
                                updatedObjectChange?.also { objectChange ->
                                    for (versionedChange in objectChange.changes) {
                                        val changes = versionedChange.changes

                                        if (changes.contains(ObjectCreate)) {
                                            getSingleValues(versionedChange.version)?.let { valuesWithMeta ->
                                                updates += AdditionUpdate(
                                                    objectChange.key,
                                                    versionedChange.version,
                                                    valuesWithMeta.firstVersion,
                                                    insertionIndex,
                                                    valuesWithMeta.isDeleted,
                                                    valuesWithMeta.values
                                                )
                                            }
                                        } else {
                                            updates += ChangeUpdate(
                                                objectChange.key,
                                                versionedChange.version,
                                                insertionIndex,
                                                changes
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    // Sort all updates on version, they are before sorted on data object order and then version
    updates.sortBy { it.version }

    lastResponseVersion = minOf(getRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)

    updates.add(0,
        OrderedKeysUpdate(
            version = lastResponseVersion,
            keys = matchingKeys
        )
    )

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = getRequest.dataModel,
            updates = updates,
            dataFetchType = FetchByKey,
        )
    )
}

private fun GetUpdatesRequest<*>.needsSoftDeleteFallback() =
    toVersion == null && (maxVersions > 1u || !filterSoftDeleted)

private fun <DM : IsRootDataModel> ValuesWithMetaData<DM>.withSoftDeleteState(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    toVersion: ULong?,
    reader: HistoricalTableReader?
): ValuesWithMetaData<DM> {
    if (toVersion == null) {
        return this
    }

    val deleted = isSoftDeleted(
        dbAccessor = dbAccessor,
        columnFamilies = columnFamilies,
        readOptions = readOptions,
        toVersion = toVersion,
        key = key,
        historicalTableReader = reader
    )
    return if (isDeleted == deleted) this else copy(isDeleted = deleted)
}

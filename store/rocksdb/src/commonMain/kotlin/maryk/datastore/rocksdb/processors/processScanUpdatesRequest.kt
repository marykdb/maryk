package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.models.fromChanges
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.getLastVersion
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.shared.Cache
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.lib.recyclableByteArray
import maryk.rocksdb.rocksDBNotFound
import maryk.rocksdb.use

internal typealias ScanUpdatesStoreAction<DM> = StoreAction<DM, ScanUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdatesStoreAction = ScanUpdatesStoreAction<IsRootDataModel>

/** Processes a ScanUpdatesRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootDataModel> processScanUpdatesRequest(
    storeAction: ScanUpdatesStoreAction<DM>,
    dataStore: RocksDBDataStore,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val dbIndex = dataStore.getDataModelId(scanRequest.dataModel)
    val columnFamilies = dataStore.getColumnFamilies(dbIndex)

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()

    var lastResponseVersion = 0uL

    var sortingKeys: MutableList<ByteArray>? = null
    var sortingIndex: IsIndexable? = null

    var insertionIndex = -1

    DBAccessor(dataStore).use { dbAccessor ->
        val columnToScan = if ((scanRequest.toVersion != null || scanRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = dbAccessor.getIterator(dataStore.defaultReadOptions, columnToScan)

        scanRequest.checkMaxVersions(dataStore.keepAllVersions)

        fun getSingleValues(key: Key<DM>, creationVersion: ULong, cacheReader: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?): ValuesWithMetaData<DM>? {
            dbAccessor.getIterator(dataStore.defaultReadOptions, columnToScan).use { deepIterator ->
                return scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                    deepIterator,
                    creationVersion,
                    columnFamilies,
                    key,
                    scanRequest.select,
                    scanRequest.toVersion,
                    cacheReader
                )
            }
        }

        processScan(
            scanRequest,
            dataStore,
            dbAccessor,
            columnFamilies,
            dataStore.defaultReadOptions,
            scanSetup = {
                (it as? IndexScan)?.let { indexScan ->
                    sortingKeys = mutableListOf()
                    sortingIndex = indexScan.index
                }
            }
        ) { key, creationVersion, sortingKey ->
            insertionIndex++

            matchingKeys.add(key)

            // Add sorting index
            sortingIndex?.let {
                val storeGetter = DBAccessorStoreValuesGetter(columnFamilies, dataStore.defaultReadOptions)
                storeGetter.moveToKey(key.bytes, dbAccessor, scanRequest.toVersion)

                it.toStorageByteArrayForIndex(storeGetter, key.bytes)?.let { indexableBytes ->
                    sortingKeys?.add(indexableBytes)
                }
            }

            val lastVersion = getLastVersion(dbAccessor, columnFamilies, dataStore.defaultReadOptions, key)
            lastResponseVersion = maxOf(lastResponseVersion, lastVersion)

            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                cache.readValue(dbIndex, key, reference, version, valueReader)
            }

            scanRequest.dataModel.readTransactionIntoObjectChanges(
                iterator,
                creationVersion,
                columnFamilies,
                key,
                scanRequest.select,
                scanRequest.fromVersion,
                scanRequest.toVersion,
                scanRequest.maxVersions,
                sortingKey,
                cacheReader
            )?.let { objectChange ->
                updates += objectChange.changes.mapNotNull { versionedChange ->
                    val changes = versionedChange.changes

                    if (changes.contains(ObjectCreate)) {
                        val addedValues = scanRequest.dataModel.fromChanges(null, changes)

                        AdditionUpdate(
                            key = objectChange.key,
                            version = versionedChange.version,
                            firstVersion = versionedChange.version,
                            insertionIndex = insertionIndex,
                            isDeleted = false,
                            values = addedValues
                        )
                    } else {
                        if (scanRequest.orderedKeys?.contains(objectChange.key) != false) {
                            ChangeUpdate(
                                key = objectChange.key,
                                version = versionedChange.version,
                                index = insertionIndex,
                                changes = changes
                            )
                        } else {
                            getSingleValues(key, creationVersion, cacheReader)?.let { valuesWithMeta ->
                                AdditionUpdate(
                                    key = objectChange.key,
                                    version = versionedChange.version,
                                    firstVersion = valuesWithMeta.firstVersion,
                                    insertionIndex = insertionIndex,
                                    isDeleted = valuesWithMeta.isDeleted,
                                    values = valuesWithMeta.values
                                )
                            }
                        }
                    }
                }
            }
        }

        iterator.close()

        // Sort all updates on version, they are before sorted on data object order and then version
        updates.sortBy { it.version }

        lastResponseVersion = minOf(scanRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)

        updates.add(0,
            OrderedKeysUpdate(
                version = lastResponseVersion,
                keys = matchingKeys,
                sortingKeys = sortingKeys?.map { Bytes(it) }
            )
        )

        scanRequest.orderedKeys?.let { orderedKeys ->
            // Remove values which should or should not be there from passed orderedKeys
            // This so the requester is up-to-date with any in between filtered values
            orderedKeys.subtract(matchingKeys.toSet()).let { removedKeys ->
                for (removedKey in removedKeys) {
                    val createdVersionLength = dbAccessor.get(columnFamilies.keys, dataStore.defaultReadOptions, removedKey.bytes, recyclableByteArray)

                    updates += RemovalUpdate(
                        key = removedKey,
                        version = lastResponseVersion,
                        reason = when {
                            createdVersionLength == rocksDBNotFound ->
                                HardDelete
                            isSoftDeleted(dbAccessor, columnFamilies, dataStore.defaultReadOptions, scanRequest.toVersion, removedKey.bytes) ->
                                SoftDelete
                            else -> NotInRange
                        }
                    )
                }
            }

            matchingKeys.subtract(orderedKeys.toSet()).let { addedKeys ->
                for (addedKey in addedKeys) {
                    val valueLength = dbAccessor.get(columnFamilies.keys, dataStore.defaultReadOptions, addedKey.bytes, recyclableByteArray)
                    // Only process it if it was created
                    if (valueLength != rocksDBNotFound) {
                        val createdVersion = recyclableByteArray.readVersionBytes()

                        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                            cache.readValue(dbIndex, addedKey, reference, version, valueReader)
                        }

                        getSingleValues(addedKey, createdVersion, cacheReader)?.let { valuesWithMeta ->
                            updates += AdditionUpdate(
                                key = addedKey,
                                version = lastResponseVersion,
                                firstVersion = valuesWithMeta.firstVersion,
                                insertionIndex = matchingKeys.indexOf(addedKey),
                                isDeleted = valuesWithMeta.isDeleted,
                                values = valuesWithMeta.values
                            )
                        }
                    }
                }
            }
        }
    }

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = scanRequest.dataModel,
            updates = updates
        )
    )
}

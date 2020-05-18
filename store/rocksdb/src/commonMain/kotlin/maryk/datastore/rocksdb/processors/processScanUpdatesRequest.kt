package maryk.datastore.rocksdb.processors

import maryk.core.extensions.bytes.toULong
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.index.IsIndexable
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
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.StoreAction
import maryk.lib.recyclableByteArray
import maryk.rocksdb.rocksDBNotFound
import maryk.rocksdb.use

internal typealias ScanUpdatesStoreAction<DM, P> = StoreAction<DM, P, ScanUpdatesRequest<DM, P>, UpdatesResponse<DM, P>>
internal typealias AnyScanUpdatesStoreAction = ScanUpdatesStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ScanUpdatesRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processScanUpdatesRequest(
    storeAction: ScanUpdatesStoreAction<DM, P>,
    dataStore: RocksDBDataStore
) {
    val scanRequest = storeAction.request
    val dbIndex = dataStore.getDataModelId(scanRequest.dataModel)
    val columnFamilies = dataStore.getColumnFamilies(dbIndex)

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM, P>>()

    var lastResponseVersion = 0uL

    var sortingKeys: MutableList<ByteArray>? = null
    var sortingIndex: IsIndexable? = null

    var insertionIndex = -1

    DBAccessor(dataStore).use { dbAccessor ->
        val columnToScan = if (scanRequest.toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = dbAccessor.getIterator(dataStore.defaultReadOptions, columnToScan)

        fun getSingleValues(key: Key<DM>, creationVersion: ULong, cacheReader: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?): ValuesWithMetaData<DM, P>? {
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
        ) { key, creationVersion ->
            insertionIndex++

            matchingKeys.add(key)

            val storeGetter = StoreValuesGetter(key.bytes, dataStore.db, columnFamilies, dataStore.defaultReadOptions)

            // Add sorting index
            sortingIndex?.toStorageByteArrayForIndex(storeGetter, key.bytes)?.let {
                sortingKeys?.add(it)
            }

            val lastVersion = getLastVersion(dbAccessor, columnFamilies, dataStore.defaultReadOptions, key)
            lastResponseVersion = maxOf(lastResponseVersion, lastVersion)

            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                dataStore.readValueWithCache(dbIndex, key, reference, version, valueReader)
            }

            scanRequest.dataModel.readTransactionIntoObjectChanges(
                iterator,
                creationVersion,
                columnFamilies,
                key,
                scanRequest.select,
                scanRequest.fromVersion,
                scanRequest.toVersion,
                cacheReader
            )?.let { objectChange ->
                updates += objectChange.changes.mapNotNull { versionedChange ->
                    val changes = versionedChange.changes

                    if (changes.contains(ObjectCreate)) {
                        val addedValues = scanRequest.dataModel.fromChanges(null, changes)

                        AdditionUpdate(
                            objectChange.key,
                            versionedChange.version,
                            insertionIndex,
                            addedValues
                        )
                    } else {
                        if (scanRequest.orderedKeys?.contains(objectChange.key) != false) {
                            ChangeUpdate(
                                objectChange.key,
                                versionedChange.version,
                                insertionIndex,
                                changes
                            )
                        } else {
                            getSingleValues(key, creationVersion, cacheReader)?.let { valuesWithMeta ->
                                AdditionUpdate(
                                    objectChange.key,
                                    versionedChange.version,
                                    insertionIndex,
                                    valuesWithMeta.values
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
            // This so the requester is up to date with any in between filtered values
            orderedKeys.subtract(matchingKeys).let { removedKeys ->
                for (removedKey in removedKeys) {
                    @Suppress("UNCHECKED_CAST")
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

            matchingKeys.subtract(orderedKeys).let { addedKeys ->
                for (addedKey in addedKeys) {
                    val valueLength = dbAccessor.get(columnFamilies.keys, dataStore.defaultReadOptions, addedKey.bytes, recyclableByteArray)
                    // Only process it if it was created
                    if (valueLength != rocksDBNotFound) {
                        val createdVersion = recyclableByteArray.toULong()

                        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                            dataStore.readValueWithCache(dbIndex, addedKey, reference, version, valueReader)
                        }

                        getSingleValues(addedKey, createdVersion, cacheReader)?.let { valuesWithMeta ->
                            updates += AdditionUpdate(
                                addedKey,
                                lastResponseVersion,
                                matchingKeys.indexOf(addedKey),
                                valuesWithMeta.values
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

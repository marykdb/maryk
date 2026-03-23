package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.responses.FetchByUpdateHistoryIndex
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
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.getLastVersion
import maryk.datastore.rocksdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.Cache
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.lib.recyclableByteArray
import maryk.rocksdb.rocksDBNotFound

internal typealias ScanUpdatesStoreAction<DM> = StoreAction<DM, ScanUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdatesStoreAction = ScanUpdatesStoreAction<IsRootDataModel>

/** Processes a ScanUpdatesRequest in a [storeAction] into a [RocksDBDataStore] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processScanUpdatesRequest(
    storeAction: ScanUpdatesStoreAction<DM>,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)

    if (scanRequest.canUseUpdateHistoryIndex() && canUseUpdateHistoryIndex(dbIndex) && columnFamilies.updateHistory != null) {
        processUpdateHistoryScanUpdates(storeAction, cache, dbIndex, columnFamilies)
        return
    }

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()

    var lastResponseVersion = 0uL

    var sortingKeys: MutableList<ByteArray>? = null
    var sortingIndex: IsIndexable? = null

    var insertionIndex = -1

    DBAccessor(this).use { dbAccessor ->
        val columnToScan = if ((scanRequest.toVersion != null || scanRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = dbAccessor.getIterator(defaultReadOptions, columnToScan)

        scanRequest.checkMaxVersions(keepAllVersions)

        fun getSingleValues(
            key: Key<DM>,
            creationVersion: ULong,
            version: ULong?,
            cacheReader: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
        ): ValuesWithMetaData<DM>? {
            val readVersion = version.takeIf { columnFamilies is HistoricTableColumnFamilies }
            val readColumn = if ((readVersion != null || scanRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
                columnFamilies.historic.table
            } else {
                columnToScan
            }

            dbAccessor.getIterator(defaultReadOptions, readColumn).use { deepIterator ->
                return scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                    deepIterator,
                    creationVersion,
                    columnFamilies,
                    key,
                    scanRequest.select,
                    readVersion,
                    cacheReader
                )?.let { values ->
                    if (readVersion == null) {
                        values
                    } else {
                        val deleted = isSoftDeleted(
                            dbAccessor,
                            columnFamilies,
                            defaultReadOptions,
                            readVersion,
                            key.bytes,
                            0,
                            key.size
                        )
                        if (values.isDeleted == deleted) values else values.copy(isDeleted = deleted)
                    }
                }
            }
        }

        val dataFetchType = processScan(
            scanRequest,
            dbAccessor,
            columnFamilies,
            defaultReadOptions,
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
                val storeGetter = DBAccessorStoreValuesGetter(columnFamilies, defaultReadOptions)
                storeGetter.moveToKey(key.bytes, dbAccessor, scanRequest.toVersion)

                it.toStorageByteArrayForIndex(storeGetter, key.bytes)?.let { indexableBytes ->
                    sortingKeys?.add(indexableBytes)
                }
            }

            val lastVersion = getLastVersion(dbAccessor, columnFamilies, defaultReadOptions, key)
            lastResponseVersion = maxOf(lastResponseVersion, lastVersion)

            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                runBlocking {
                    cache.readValue(dbIndex, key, reference, version, valueReader)
                }
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
            )?.let { changes ->
                if (scanRequest.needsSoftDeleteFallback() && columnFamilies is HistoricTableColumnFamilies) {
                    addSoftDeleteChangeIfMissing(
                        dbAccessor = dbAccessor,
                        columnFamilies = columnFamilies,
                        readOptions = defaultReadOptions,
                        key = key,
                        fromVersion = scanRequest.fromVersion,
                        objectChange = changes
                    )
                } else {
                    changes
                }
            }?.let { objectChange ->
                updates += objectChange.changes.mapNotNull { versionedChange ->
                    val changes = versionedChange.changes

                    if (changes.contains(ObjectCreate)) {
                        getSingleValues(key, creationVersion, versionedChange.version, cacheReader)?.let { valuesWithMeta ->
                            AdditionUpdate(
                                key = objectChange.key,
                                version = versionedChange.version,
                                firstVersion = valuesWithMeta.firstVersion,
                                insertionIndex = insertionIndex,
                                isDeleted = valuesWithMeta.isDeleted,
                                values = valuesWithMeta.values
                            )
                        }
                    } else {
                        if (scanRequest.orderedKeys?.contains(objectChange.key) != false) {
                            ChangeUpdate(
                                key = objectChange.key,
                                version = versionedChange.version,
                                index = insertionIndex,
                                changes = changes
                            )
                        } else {
                            getSingleValues(key, creationVersion, scanRequest.toVersion, cacheReader)?.let { valuesWithMeta ->
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
                    val createdVersionLength = dbAccessor.get(columnFamilies.keys, defaultReadOptions, removedKey.bytes, recyclableByteArray)

                    updates += RemovalUpdate(
                        key = removedKey,
                        version = lastResponseVersion,
                        reason = when {
                            createdVersionLength == rocksDBNotFound ->
                                HardDelete
                            isSoftDeleted(dbAccessor, columnFamilies, defaultReadOptions, scanRequest.toVersion, removedKey.bytes) ->
                                SoftDelete
                            else -> NotInRange
                        }
                    )
                }
            }

            matchingKeys.subtract(orderedKeys.toSet()).let { addedKeys ->
                for (addedKey in addedKeys) {
                    val valueLength = dbAccessor.get(columnFamilies.keys, defaultReadOptions, addedKey.bytes, recyclableByteArray)
                    // Only process it if it was created
                    if (valueLength != rocksDBNotFound) {
                        val createdVersion = recyclableByteArray.readVersionBytes()

                        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                            runBlocking {
                                cache.readValue(dbIndex, addedKey, reference, version, valueReader)
                            }
                        }

                        getSingleValues(addedKey, createdVersion, scanRequest.toVersion, cacheReader)?.let { valuesWithMeta ->
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

        storeAction.response.complete(
            UpdatesResponse(
                dataModel = scanRequest.dataModel,
                updates = updates,
                dataFetchType = dataFetchType,
            )
        )
    }
}

private fun <DM : IsRootDataModel> RocksDBDataStore.processUpdateHistoryScanUpdates(
    storeAction: ScanUpdatesStoreAction<DM>,
    cache: Cache,
    dbIndex: UInt,
    columnFamilies: TableColumnFamilies
) {
    val scanRequest = storeAction.request
    val keySize = scanRequest.dataModel.Meta.keyByteSize
    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()
    val scanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes, scanRequest.includeStart)
    var lastResponseVersion = 0uL

    DBAccessor(this).use { dbAccessor ->
        fun getSingleValues(
            key: Key<DM>,
            creationVersion: ULong,
            version: ULong?,
            cacheReader: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
        ): ValuesWithMetaData<DM>? {
            val readVersion = version.takeIf { columnFamilies is HistoricTableColumnFamilies }
            val readColumn = if ((readVersion != null || scanRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
                columnFamilies.historic.table
            } else columnFamilies.table
            dbAccessor.getIterator(defaultReadOptions, readColumn).use { deepIterator ->
                return scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                    deepIterator,
                    creationVersion,
                    columnFamilies,
                    key,
                    scanRequest.select,
                    readVersion,
                    cacheReader
                )?.let { values ->
                    if (readVersion == null) {
                        values
                    } else {
                        val deleted = isSoftDeleted(
                            dbAccessor,
                            columnFamilies,
                            defaultReadOptions,
                            readVersion,
                            key.bytes,
                            0,
                            key.size
                        )
                        if (values.isDeleted == deleted) values else values.copy(isDeleted = deleted)
                    }
                }
            }
        }

        scanRequest.checkMaxVersions(keepAllVersions)
        val historyIterator = dbAccessor.getIterator(defaultReadOptions, columnFamilies.updateHistory!!)
        val seenKeys = mutableSetOf<Key<DM>>()

        when (val toVersion = scanRequest.toVersion) {
            null -> historyIterator.seekToFirst()
            else -> historyIterator.seek(toVersion.toReversedVersionBytes())
        }

        while (historyIterator.isValid() && matchingKeys.size.toUInt() < scanRequest.limit) {
            val historyKey = historyIterator.key()
            val version = historyKey.readReversedVersionBytes(0)
            if (version < scanRequest.fromVersion) {
                break
            }

            val key = Key<DM>(historyKey.copyOfRange(VERSION_BYTE_SIZE, VERSION_BYTE_SIZE + keySize))
            if (!seenKeys.add(key)) {
                historyIterator.next()
                continue
            }

            val isHardDelete = historyIterator.value().firstOrNull() == 1.toByte()
            val createdVersionLength = dbAccessor.get(columnFamilies.keys, defaultReadOptions, key.bytes, recyclableByteArray)
            if (createdVersionLength == rocksDBNotFound) {
                if (isHardDelete && scanRequest.where == null &&
                    !scanRange.keyBeforeStart(key.bytes, 0) && scanRange.keyWithinRanges(key.bytes, 0) && scanRange.matchesPartials(key.bytes) &&
                    version >= scanRequest.fromVersion
                ) {
                    updates += RemovalUpdate(
                        key = key,
                        version = version,
                        reason = HardDelete
                    )
                    lastResponseVersion = maxOf(lastResponseVersion, version)
                }
                historyIterator.next()
                continue
            }
            val creationVersion = recyclableByteArray.readVersionBytes()

            if (scanRequest.shouldBeFiltered(dbAccessor, columnFamilies, defaultReadOptions, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion)
                || scanRange.keyBeforeStart(key.bytes, 0)
                || !scanRange.keyWithinRanges(key.bytes, 0)
                || !scanRange.matchesPartials(key.bytes)
            ) {
                historyIterator.next()
                continue
            }

            matchingKeys += key
            lastResponseVersion = maxOf(lastResponseVersion, getLastVersion(dbAccessor, columnFamilies, defaultReadOptions, key))

            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, readVersion: ULong, valueReader: () -> Any? ->
                runBlocking {
                    cache.readValue(dbIndex, key, reference, readVersion, valueReader)
                }
            }

            val readColumn = if ((scanRequest.toVersion != null || scanRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
                columnFamilies.historic.table
            } else {
                columnFamilies.table
            }

            dbAccessor.getIterator(defaultReadOptions, readColumn).use { objectIterator ->
                scanRequest.dataModel.readTransactionIntoObjectChanges(
                    objectIterator,
                    creationVersion,
                    columnFamilies,
                    key,
                    scanRequest.select,
                    scanRequest.fromVersion,
                    scanRequest.toVersion,
                    scanRequest.maxVersions,
                    null,
                    cacheReader
                )
            }?.let { changes ->
                if (scanRequest.needsSoftDeleteFallback() && columnFamilies is HistoricTableColumnFamilies) {
                    addSoftDeleteChangeIfMissing(
                        dbAccessor = dbAccessor,
                        columnFamilies = columnFamilies,
                        readOptions = defaultReadOptions,
                        key = key,
                        fromVersion = scanRequest.fromVersion,
                        objectChange = changes
                    )
                } else {
                    changes
                }
            }?.let { objectChange ->
                updates += objectChange.changes.mapNotNull { versionedChange ->
                    val changes = versionedChange.changes

                    if (changes.contains(ObjectCreate)) {
                        getSingleValues(key, creationVersion, versionedChange.version, cacheReader)?.let { valuesWithMeta ->
                            AdditionUpdate(
                                key = objectChange.key,
                                version = versionedChange.version,
                                firstVersion = valuesWithMeta.firstVersion,
                                insertionIndex = matchingKeys.lastIndex,
                                isDeleted = valuesWithMeta.isDeleted,
                                values = valuesWithMeta.values
                            )
                        }
                    } else {
                        if (scanRequest.orderedKeys?.contains(objectChange.key) != false) {
                            ChangeUpdate(
                                key = objectChange.key,
                                version = versionedChange.version,
                                index = matchingKeys.lastIndex,
                                changes = changes
                            )
                        } else {
                            getSingleValues(key, creationVersion, scanRequest.toVersion, cacheReader)?.let { valuesWithMeta ->
                                AdditionUpdate(
                                    key = objectChange.key,
                                    version = versionedChange.version,
                                    firstVersion = valuesWithMeta.firstVersion,
                                    insertionIndex = matchingKeys.lastIndex,
                                    isDeleted = valuesWithMeta.isDeleted,
                                    values = valuesWithMeta.values
                                )
                            }
                        }
                    }
                }
            }
            historyIterator.next()
        }

        historyIterator.close()
    }

    updates.sortBy { it.version }
    lastResponseVersion = minOf(scanRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)
    updates.add(
        0,
        OrderedKeysUpdate(
            version = lastResponseVersion,
            keys = matchingKeys,
            sortingKeys = null
        )
    )

    DBAccessor(this).use { dbAccessor ->
        scanRequest.orderedKeys?.let { orderedKeys ->
            orderedKeys.subtract(matchingKeys.toSet()).forEach { removedKey ->
                val createdVersionLength = dbAccessor.get(columnFamilies.keys, defaultReadOptions, removedKey.bytes, recyclableByteArray)
                updates += RemovalUpdate(
                    key = removedKey,
                    version = lastResponseVersion,
                    reason = when {
                        createdVersionLength == rocksDBNotFound -> HardDelete
                        isSoftDeleted(dbAccessor, columnFamilies, defaultReadOptions, scanRequest.toVersion, removedKey.bytes) -> SoftDelete
                        else -> NotInRange
                    }
                )
            }

            matchingKeys.subtract(orderedKeys.toSet()).forEach { addedKey ->
                val createdVersionLength = dbAccessor.get(columnFamilies.keys, defaultReadOptions, addedKey.bytes, recyclableByteArray)
                if (createdVersionLength != rocksDBNotFound) {
                    val creationVersion = recyclableByteArray.readVersionBytes()
                    val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, readVersion: ULong, valueReader: () -> Any? ->
                        runBlocking {
                            cache.readValue(dbIndex, addedKey, reference, readVersion, valueReader)
                        }
                    }
                    val readColumn = if ((scanRequest.toVersion != null || scanRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
                        columnFamilies.historic.table
                    } else {
                        columnFamilies.table
                    }

                    dbAccessor.getIterator(defaultReadOptions, readColumn).use { objectIterator ->
                        scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                            objectIterator,
                            creationVersion,
                            columnFamilies,
                            addedKey,
                            scanRequest.select,
                            scanRequest.toVersion,
                            cacheReader
                        )?.let { valuesWithMeta ->
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
            updates = updates,
            dataFetchType = FetchByUpdateHistoryIndex(),
        )
    )
}

private fun ScanUpdatesRequest<*>.canUseUpdateHistoryIndex() =
    order == null && fromVersion == 0uL && toVersion == null && maxVersions == 1u

private fun ScanUpdatesRequest<*>.needsSoftDeleteFallback() =
    toVersion == null && (maxVersions > 1u || !filterSoftDeleted)

package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.models.key
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
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
import maryk.core.values.IsValuesGetter
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.getLastVersion
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.readMapByReference
import maryk.datastore.foundationdb.processors.helpers.readSetByReference
import maryk.datastore.shared.Cache
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.helpers.convertToValue
import maryk.foundationdb.ReadTransaction
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareTo
import maryk.foundationdb.Range as FDBRange

internal typealias ScanUpdatesStoreAction<DM> = StoreAction<DM, ScanUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdatesStoreAction = ScanUpdatesStoreAction<IsRootDataModel>

/** Processes a ScanUpdatesRequest in a [storeAction] into a [FoundationDBDataStore] */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScanUpdatesRequest(
    storeAction: ScanUpdatesStoreAction<DM>,
    cache: Cache,
) {
    val scanRequest = storeAction.request
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    if (scanRequest.order == null && canUseUpdateHistoryIndex(dbIndex) && tableDirs.updateHistoryPrefix != null) {
        processUpdateHistoryScanUpdates(storeAction, cache, dbIndex, tableDirs)
        return
    }

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()

    var lastResponseVersion = 0uL

    var sortingKeys: MutableList<ByteArray>? = null
    var sortingIndex: IsIndexable? = null

    var insertionIndex = -1

    scanRequest.checkMaxVersions(keepAllVersions)

    runTransaction { tr ->
        fun getSingleValues(
            key: Key<DM>,
            creationVersion: ULong,
            cacheReader: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
        ): ValuesWithMetaData<DM>? {
            return scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                tr = tr,
                creationVersion = creationVersion,
                tableDirs = tableDirs,
                key = key,
                select = scanRequest.select,
                toVersion = scanRequest.toVersion,
                cachedRead = cacheReader,
                decryptValue = this@processScanUpdatesRequest::decryptValueIfNeeded
            )
        }

        val dataFetchType = this.processScan(
            tr = tr,
            scanRequest = scanRequest,
            tableDirs = tableDirs,
            scanSetup = {
                (it as? IndexScan)?.let { indexScan ->
                    sortingKeys = mutableListOf()
                    sortingIndex = indexScan.index
                }
            },
        ) { key, creationVersion, sortingKey ->
            insertionIndex++

            matchingKeys.add(key)

            // Add sorting index if requested
            sortingIndex?.let { idx ->
                val getter = StoreValuesGetter(tr, tableDirs, this@processScanUpdatesRequest::decryptValueIfNeeded)
                getter.moveToKey(key.bytes, scanRequest.toVersion)
                idx.toStorageByteArrayForIndex(getter, key.bytes)?.let { indexableBytes ->
                    sortingKeys?.add(indexableBytes)
                }
            }

            // Determine last known version for ordered response metadata
            val last = getLastVersion(tr, tableDirs, key)
            lastResponseVersion = maxOf(lastResponseVersion, last)

            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                cache.readValue(dbIndex, key, reference, version, valueReader)
            }

            val objectChange = scanRequest.dataModel.readTransactionIntoObjectChanges(
                tr = tr,
                creationVersion = creationVersion,
                tableDirs = tableDirs,
                key = key,
                select = scanRequest.select,
                fromVersion = scanRequest.fromVersion,
                toVersion = scanRequest.toVersion,
                maxVersions = scanRequest.maxVersions,
                sortingKey = sortingKey,
                cachedRead = cacheReader
            )?.let { changes ->
                if (scanRequest.toVersion == null && scanRequest.maxVersions > 1u && tableDirs is HistoricTableDirectories) {
                    addSoftDeleteChangeIfMissing(
                        tr = tr,
                        tableDirs = tableDirs,
                        key = key,
                        fromVersion = scanRequest.fromVersion,
                        objectChange = changes
                    )
                } else {
                    changes
                }
            }

            objectChange?.let { oc ->
                for (versionedChange in oc.changes) {
                    val changes = versionedChange.changes
                    val update = if (changes.find { it is ObjectCreate } != null) {
                        val addedValues = scanRequest.dataModel.fromChanges(null, changes)
                        AdditionUpdate(
                            key = oc.key,
                            version = versionedChange.version,
                            firstVersion = versionedChange.version,
                            insertionIndex = insertionIndex,
                            isDeleted = false,
                            values = addedValues
                        )
                    } else {
                        if (scanRequest.orderedKeys?.contains(oc.key) != false) {
                            ChangeUpdate(
                                key = oc.key,
                                version = versionedChange.version,
                                index = insertionIndex,
                                changes = changes
                            )
                        } else {
                            getSingleValues(key, creationVersion, cacheReader)?.let { valuesWithMeta ->
                                AdditionUpdate(
                                    key = oc.key,
                                    version = versionedChange.version,
                                    firstVersion = valuesWithMeta.firstVersion,
                                    insertionIndex = insertionIndex,
                                    isDeleted = valuesWithMeta.isDeleted,
                                    values = valuesWithMeta.values
                                )
                            }
                        }
                    }
                    update?.also { updates += it }
                }
            }
        }

        // Sort updates by version ascending
        updates.sortBy { it.version }

        lastResponseVersion = minOf(scanRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)

        updates.add(
            0,
            OrderedKeysUpdate(
                version = lastResponseVersion,
                keys = matchingKeys,
                sortingKeys = sortingKeys?.map { Bytes(it) }
            )
        )

        // orderedKeys reconciliation
        scanRequest.orderedKeys?.let { orderedKeys ->
            // Removals for keys no longer in range
            orderedKeys.subtract(matchingKeys.toSet()).let { removedKeys ->
                for (removedKey in removedKeys) {
                    val exists = tr.get(packKey(tableDirs.keysPrefix, removedKey.bytes)).awaitResult()
                    updates += RemovalUpdate(
                        key = removedKey,
                        version = lastResponseVersion,
                        reason = when {
                            exists == null -> HardDelete
                            isSoftDeleted(tr, tableDirs, scanRequest.toVersion, removedKey.bytes) -> SoftDelete
                            else -> NotInRange
                        }
                    )
                }
            }

            // Additions for keys newly added in range relative to old orderedKeys
            matchingKeys.subtract(orderedKeys.toSet()).let { addedKeys ->
                for (addedKey in addedKeys) {
                    val createdBytes = tr.get(packKey(tableDirs.keysPrefix, addedKey.bytes)).awaitResult()
                    if (createdBytes != null) {
                        val createdVersion = HLC.fromStorageBytes(createdBytes).timestamp

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

        storeAction.response.complete(
            UpdatesResponse(
                dataModel = scanRequest.dataModel,
                updates = updates,
                dataFetchType = dataFetchType,
            )
        )
    }
}

private fun <DM : IsRootDataModel> FoundationDBDataStore.processUpdateHistoryScanUpdates(
    storeAction: ScanUpdatesStoreAction<DM>,
    cache: Cache,
    dbIndex: UInt,
    tableDirs: IsTableDirectories,
) {
    val scanRequest = storeAction.request
    val keySize = scanRequest.dataModel.Meta.keyByteSize
    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()
    val scanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes, scanRequest.includeStart)
    var lastResponseVersion = 0uL

    scanRequest.checkMaxVersions(keepAllVersions)

    runTransaction { tr ->
        val seenKeys = mutableSetOf<Key<DM>>()
        val historyIterator = tr.getRange(FDBRange.startsWith(tableDirs.updateHistoryPrefix!!), ReadTransaction.ROW_LIMIT_UNLIMITED, false).iterator()
        val groupedEntries = mutableMapOf<Key<DM>, Boolean>()
        var currentGroupVersion: ULong? = null

        fun processEntry(key: Key<DM>, version: ULong, isHardDelete: Boolean) {
            val createdBytes = tr.get(packKey(tableDirs.keysPrefix, key.bytes)).awaitResult()
            if (createdBytes == null) {
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
                return
            }
            val creationVersion = HLC.fromStorageBytes(createdBytes).timestamp

            if (scanRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion, this@processUpdateHistoryScanUpdates::decryptValueIfNeeded)
                || scanRange.keyBeforeStart(key.bytes, 0)
                || !scanRange.keyWithinRanges(key.bytes, 0)
                || !scanRange.matchesPartials(key.bytes)
            ) {
                return
            }

            matchingKeys += key
            lastResponseVersion = maxOf(lastResponseVersion, getLastVersion(tr, tableDirs, key))

            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, readVersion: ULong, valueReader: () -> Any? ->
                cache.readValue(dbIndex, key, reference, readVersion, valueReader)
            }

            scanRequest.dataModel.readTransactionIntoObjectChanges(
                tr = tr,
                creationVersion = creationVersion,
                tableDirs = tableDirs,
                key = key,
                select = scanRequest.select,
                fromVersion = scanRequest.fromVersion,
                toVersion = scanRequest.toVersion,
                maxVersions = scanRequest.maxVersions,
                sortingKey = null,
                cachedRead = cacheReader
            )?.let { changes ->
                if (scanRequest.toVersion == null && scanRequest.maxVersions > 1u && tableDirs is HistoricTableDirectories) {
                    addSoftDeleteChangeIfMissing(
                        tr = tr,
                        tableDirs = tableDirs,
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
                        val addedValues = scanRequest.dataModel.fromChanges(null, changes)

                        AdditionUpdate(
                            key = objectChange.key,
                            version = versionedChange.version,
                            firstVersion = versionedChange.version,
                            insertionIndex = matchingKeys.lastIndex,
                            isDeleted = false,
                            values = addedValues
                        )
                    } else {
                        if (scanRequest.orderedKeys?.contains(objectChange.key) != false) {
                            ChangeUpdate(
                                key = objectChange.key,
                                version = versionedChange.version,
                                index = matchingKeys.lastIndex,
                                changes = changes
                            )
                        } else {
                            scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                                tr = tr,
                                creationVersion = creationVersion,
                                tableDirs = tableDirs,
                                key = key,
                                select = scanRequest.select,
                                toVersion = scanRequest.toVersion,
                                cachedRead = cacheReader,
                                decryptValue = this@processUpdateHistoryScanUpdates::decryptValueIfNeeded
                            )?.let { valuesWithMeta ->
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
        }

        fun flushGroupedEntries(): Boolean {
            if (currentGroupVersion == null) return false

            for ((key, isHardDelete) in groupedEntries.entries.sortedWith { a, b ->
                b.key.bytes compareTo a.key.bytes
            }) {
                seenKeys.add(key)
                processEntry(key, currentGroupVersion!!, isHardDelete)
                if (matchingKeys.size.toUInt() >= scanRequest.limit) {
                    groupedEntries.clear()
                    return true
                }
            }
            groupedEntries.clear()
            return false
        }

        while (historyIterator.hasNext()) {
            val historyEntry = historyIterator.nextBlocking()
            val historyKey = historyEntry.key
            val versionOffset = tableDirs.updateHistoryPrefix!!.size
            val version = historyKey.readReversedVersionBytes(versionOffset)
            if (version > (scanRequest.toVersion ?: ULong.MAX_VALUE)) continue

            if (currentGroupVersion != null && version != currentGroupVersion) {
                if (flushGroupedEntries()) break
            }
            currentGroupVersion = version

            val keyOffset = versionOffset + VERSION_BYTE_SIZE
            val key = scanRequest.dataModel.key(historyKey.copyOfRange(keyOffset, keyOffset + keySize))
            if (key in seenKeys || key in groupedEntries) continue

            groupedEntries[key] = historyEntry.value.firstOrNull() == 1.toByte()
        }

        if (matchingKeys.size.toUInt() < scanRequest.limit) {
            flushGroupedEntries()
        }

        scanRequest.orderedKeys?.let { orderedKeys ->
            orderedKeys.subtract(matchingKeys.toSet()).forEach { removedKey ->
                val exists = tr.get(packKey(tableDirs.keysPrefix, removedKey.bytes)).awaitResult()
                updates += RemovalUpdate(
                    key = removedKey,
                    version = lastResponseVersion,
                    reason = when {
                        exists == null -> HardDelete
                        isSoftDeleted(tr, tableDirs, scanRequest.toVersion, removedKey.bytes) -> SoftDelete
                        else -> NotInRange
                    }
                )
            }

            matchingKeys.subtract(orderedKeys.toSet()).forEach { addedKey ->
                val createdBytes = tr.get(packKey(tableDirs.keysPrefix, addedKey.bytes)).awaitResult()
                if (createdBytes != null) {
                    val creationVersion = HLC.fromStorageBytes(createdBytes).timestamp
                    val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, readVersion: ULong, valueReader: () -> Any? ->
                        cache.readValue(dbIndex, addedKey, reference, readVersion, valueReader)
                    }
                    scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                        tr = tr,
                        creationVersion = creationVersion,
                        tableDirs = tableDirs,
                        key = addedKey,
                        select = scanRequest.select,
                        toVersion = scanRequest.toVersion,
                        cachedRead = cacheReader,
                        decryptValue = this@processUpdateHistoryScanUpdates::decryptValueIfNeeded
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

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = scanRequest.dataModel,
            updates = updates,
            dataFetchType = FetchByUpdateHistoryIndex(),
        )
    )
}

/** Simple getter to compute index values for current key within a single transaction */
private class StoreValuesGetter(
    private val tr: maryk.foundationdb.Transaction,
    private val tableDirs: IsTableDirectories,
    private val decryptValue: ((ByteArray) -> ByteArray)? = null
) : IsValuesGetter {
    private lateinit var keyBytes: ByteArray
    private val cache = mutableMapOf<IsPropertyReference<*, *, *>, Any?>()

    fun moveToKey(keyBytes: ByteArray, toVersion: ULong?) {
        this.keyBytes = keyBytes
        this.toVersion = toVersion
        cache.clear()
    }

    private var toVersion: ULong? = null

    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
        propertyReference: IsPropertyReference<T, D, C>
    ): T? {
        if (cache.containsKey(propertyReference)) {
            @Suppress("UNCHECKED_CAST")
            return cache[propertyReference] as T?
        }

        val value = if (toVersion == null && propertyReference is IsMapReference<*, *, *, *>) {
            @Suppress("UNCHECKED_CAST")
            tr.readMapByReference(
                tableDirs.tablePrefix,
                keyBytes,
                propertyReference as IsMapReference<Any, Any, IsPropertyContext, *>,
                decryptValue
            ) as T?
        } else if (toVersion == null && propertyReference is SetReference<*, *>) {
            @Suppress("UNCHECKED_CAST")
            tr.readSetByReference(
                tableDirs.tablePrefix,
                keyBytes,
                propertyReference as SetReference<Any, IsPropertyContext>
            ) as T?
        } else {
            val keyAndRef = combineToByteArray(keyBytes, propertyReference.toStorageByteArray())
            tr.getValue(
                tableDirs,
                toVersion,
                keyAndRef,
                keyBytes.size,
                decryptValue = decryptValue
            ) { valueBytes, offset, length ->
                valueBytes.convertToValue(propertyReference, offset, length) as T?
            }
        }

        cache[propertyReference] = value
        return value
    }
}

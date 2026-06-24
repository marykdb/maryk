package maryk.datastore.foundationdb.processors

import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.models.IsRootDataModel
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
import maryk.core.query.orders.Direction
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
import maryk.datastore.foundationdb.processors.helpers.DecryptValue
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.forEachInRangeBatch
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.readCreationVersion
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfExact
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.readMapByReference
import maryk.datastore.foundationdb.processors.helpers.readSetByReference
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.Cache
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.helpers.convertToValue
import maryk.foundationdb.Transaction
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.foundationdb.Range as FDBRange

internal typealias ScanUpdatesStoreAction<DM> = StoreAction<DM, ScanUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdatesStoreAction = ScanUpdatesStoreAction<IsRootDataModel>
private val nextKeySuffix = byteArrayOf(0)

/** Processes a ScanUpdatesRequest in a [storeAction] into a [FoundationDBDataStore] */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScanUpdatesRequest(
    storeAction: ScanUpdatesStoreAction<DM>,
    cache: Cache,
) {
    val scanRequest = storeAction.request
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    if (scanRequest.canUseUpdateHistoryIndex() && canUseUpdateHistoryIndex(dbIndex) && tableDirs.updateHistoryPrefix != null) {
        processUpdateHistoryScanUpdates(storeAction, cache, dbIndex, tableDirs)
        return
    }

    val expectedSize = scanRequest.limit.toInt().coerceAtLeast(4)
    val matchingKeys = ArrayList<Key<DM>>(expectedSize)
    val matchingVersions = mutableMapOf<Key<DM>, ULong>()
    val updates = ArrayList<IsUpdateResponse<DM>>(expectedSize + 1)
    val keyScanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes, scanRequest.includeStart)

    var lastResponseVersion = 0uL

    var sortingKeys: MutableList<ByteArray>? = null
    var sortingIndex: IsIndexable? = null
    var sortingDirection: Direction? = null
    var sortingIndexScanRange: maryk.core.processors.datastore.scanRange.IndexableScanRanges? = null

    var insertionIndex = -1

    scanRequest.checkMaxVersions(keepAllVersions)

    fun getSingleValues(
        tr: Transaction,
        key: Key<DM>,
        creationVersion: ULong,
        version: ULong?,
        cacheReader: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
    ): ValuesWithMetaData<DM>? {
        val readVersion = version.takeIf { tableDirs is HistoricTableDirectories }
        return scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
            tr = tr,
            creationVersion = creationVersion,
            tableDirs = tableDirs,
            key = key,
            select = scanRequest.select,
            toVersion = readVersion,
            cachedRead = cacheReader,
            decryptValue = this@processScanUpdatesRequest::decryptValueIfNeeded
        )?.withSoftDeleteState(tr, tableDirs, key.bytes, readVersion)
    }

    fun selectSortingKey(getter: IsValuesGetter, index: IsIndexable, keyBytes: ByteArray): ByteArray? {
        val allIndexValues = index.toStorageByteArraysForIndex(getter, keyBytes)
        return allIndexValues
            .filter { indexValue ->
                sortingIndexScanRange?.let { scanRange ->
                    scanRange.keyWithinRanges(indexValue) && scanRange.matchesPartials(indexValue)
                } ?: true
            }
            .ifEmpty { allIndexValues }
            .let { candidateIndexValues ->
                when (sortingDirection) {
                    Direction.ASC -> candidateIndexValues.minWithOrNull { a, b -> a compareTo b }
                    Direction.DESC -> candidateIndexValues.maxWithOrNull { a, b -> a compareTo b }
                    null -> candidateIndexValues.firstOrNull()
                }
            }
    }

    val dataFetchType = this.processScan(
        scanRequest = scanRequest,
        tableDirs = tableDirs,
        scanSetup = {
            (it as? IndexScan)?.let { indexScan ->
                sortingKeys = mutableListOf()
                sortingIndex = indexScan.index
                sortingDirection = indexScan.direction
                sortingIndexScanRange = indexScan.index.createScanRange(scanRequest.where, keyScanRange)
            }
        },
    ) { tr, key, creationVersion, sortingKey ->
            insertionIndex++

            matchingKeys.add(key)

            // Add sorting index if requested
            sortingIndex?.let { idx ->
                sortingKey?.let { indexableBytes ->
                    sortingKeys?.add(indexableBytes)
                } ?: run {
                    val getter = StoreValuesGetter(tr, tableDirs, this@processScanUpdatesRequest::decryptValueIfNeeded)
                    getter.moveToKey(key.bytes, scanRequest.toVersion)
                    selectSortingKey(getter, idx, key.bytes)?.let { indexableBytes ->
                        sortingKeys?.add(indexableBytes)
                    }
                }
            }

            // Determine last known version for ordered response metadata
            val last = tr.get(packKey(tableDirs.tablePrefix, key.bytes)).awaitResult()
                ?.readHLCTimestampIfExact()
                ?: scanRequest.toVersion
                ?: return@processScan
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
            )
            val updatedObjectChange = if (scanRequest.needsSoftDeleteFallback() && tableDirs is HistoricTableDirectories) {
                addSoftDeleteChangeIfMissing(
                    tr = tr,
                    tableDirs = tableDirs,
                    key = key,
                    fromVersion = scanRequest.fromVersion,
                    objectChange = objectChange,
                    sortingKey = sortingKey
                )
            } else {
                objectChange
            }

            updatedObjectChange?.let { oc ->
                for (versionedChange in oc.changes) {
                    val changes = versionedChange.changes
                    val update = if (changes.find { it is ObjectCreate } != null) {
                        getSingleValues(tr, key, creationVersion, versionedChange.version, cacheReader)?.let { valuesWithMeta ->
                            AdditionUpdate(
                                key = oc.key,
                                version = versionedChange.version,
                                firstVersion = valuesWithMeta.firstVersion,
                                insertionIndex = insertionIndex,
                                isDeleted = valuesWithMeta.isDeleted,
                                values = valuesWithMeta.values
                            )
                        }
                    } else {
                        if (scanRequest.orderedKeys?.contains(oc.key) != false) {
                            ChangeUpdate(
                                key = oc.key,
                                version = versionedChange.version,
                                index = insertionIndex,
                                changes = changes
                            )
                        } else {
                            getSingleValues(tr, key, creationVersion, scanRequest.toVersion, cacheReader)?.let { valuesWithMeta ->
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

    runTransaction { tr ->
        // orderedKeys reconciliation
        scanRequest.orderedKeys?.let { orderedKeys ->
            val matchingKeysSet = matchingKeys.toHashSet()
            val orderedKeysSet = orderedKeys.toHashSet()

            // Removals for keys no longer in range
            orderedKeys.subtract(matchingKeysSet).let { removedKeys ->
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
            matchingKeys.subtract(orderedKeysSet).let { addedKeys ->
                for (addedKey in addedKeys) {
                    val createdVersion = tr.readCreationVersion(tableDirs, addedKey.bytes, scanRequest.toVersion)
                    if (createdVersion != null) {

                        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                            cache.readValue(dbIndex, addedKey, reference, version, valueReader)
                        }

                        getSingleValues(tr, addedKey, createdVersion, scanRequest.toVersion, cacheReader)?.let { valuesWithMeta ->
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
            dataFetchType = dataFetchType,
        )
    )
}

private fun <DM : IsRootDataModel> FoundationDBDataStore.processUpdateHistoryScanUpdates(
    storeAction: ScanUpdatesStoreAction<DM>,
    cache: Cache,
    dbIndex: UInt,
    tableDirs: IsTableDirectories,
) {
    val scanRequest = storeAction.request
    val keySize = scanRequest.dataModel.Meta.keyByteSize
    val expectedSize = scanRequest.limit.toInt().coerceAtLeast(4)
    val matchingKeys = ArrayList<Key<DM>>(expectedSize)
    val matchingVersions = mutableMapOf<Key<DM>, ULong>()
    val matchingOrder = mutableMapOf<Key<DM>, Int>()
    val updates = ArrayList<IsUpdateResponse<DM>>(expectedSize + 1)
    val scanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes, scanRequest.includeStart)
    var lastResponseVersion = 0uL

    scanRequest.checkMaxVersions(keepAllVersions)

    val seenKeys = HashSet<Key<DM>>(expectedSize * 2)
    val historyPrefix = tableDirs.updateHistoryPrefix!!
    val historyStart = scanRequest.toVersion?.let { packKey(historyPrefix, it.toReversedVersionBytes()) } ?: historyPrefix
    val historyEnd = if (scanRequest.fromVersion == 0uL) {
        historyPrefix.nextByteInSameLength()
    } else {
        packKey(historyPrefix, scanRequest.fromVersion.toReversedVersionBytes().nextByteInSameLength())
    }
    fun <T> runScanTransaction(block: (Transaction) -> T): T =
        runTransaction { tr ->
            block(tr)
        }

    fun processEntry(tr: Transaction, key: Key<DM>, version: ULong, isHardDelete: Boolean) {
            val creationVersion = tr.readCreationVersion(tableDirs, key.bytes, scanRequest.toVersion)
            if (creationVersion == null) {
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

            if (scanRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion, this@processUpdateHistoryScanUpdates::decryptValueIfNeeded)
                || scanRange.keyBeforeStart(key.bytes, 0)
                || !scanRange.keyWithinRanges(key.bytes, 0)
                || !scanRange.matchesPartials(key.bytes)
            ) {
                return
            }

            val last = tr.get(packKey(tableDirs.tablePrefix, key.bytes)).awaitResult()
                ?.readHLCTimestampIfExact()
                ?: scanRequest.toVersion
                ?: return

            matchingKeys += key
            matchingVersions[key] = version
            matchingOrder[key] = matchingKeys.lastIndex
            lastResponseVersion = maxOf(lastResponseVersion, last)

            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, readVersion: ULong, valueReader: () -> Any? ->
                cache.readValue(dbIndex, key, reference, readVersion, valueReader)
            }
            val readVersion = scanRequest.toVersion.takeIf { tableDirs is HistoricTableDirectories }

            val objectChange = scanRequest.dataModel.readTransactionIntoObjectChanges(
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
            )
            val updatedObjectChange = if (scanRequest.needsSoftDeleteFallback() && tableDirs is HistoricTableDirectories) {
                addSoftDeleteChangeIfMissing(
                    tr = tr,
                    tableDirs = tableDirs,
                    key = key,
                    fromVersion = scanRequest.fromVersion,
                    objectChange = objectChange
                )
            } else {
                objectChange
            }
            updatedObjectChange?.let { objectChange ->
                updates += objectChange.changes.mapNotNull { versionedChange ->
                    val changes = versionedChange.changes

                    if (changes.contains(ObjectCreate)) {
                        val readChangeVersion = versionedChange.version.takeIf { tableDirs is HistoricTableDirectories }
                        scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                            tr = tr,
                            creationVersion = creationVersion,
                            tableDirs = tableDirs,
                            key = key,
                            select = scanRequest.select,
                            toVersion = readChangeVersion,
                            cachedRead = cacheReader,
                            decryptValue = this@processUpdateHistoryScanUpdates::decryptValueIfNeeded
                        )?.withSoftDeleteState(
                            tr,
                            tableDirs,
                            key.bytes,
                            readChangeVersion
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
                                toVersion = readVersion,
                                cachedRead = cacheReader,
                                decryptValue = this@processUpdateHistoryScanUpdates::decryptValueIfNeeded
                            )?.withSoftDeleteState(tr, tableDirs, key.bytes, readVersion)?.let { valuesWithMeta ->
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

    var nextStart = historyStart
    while (true) {
        val result = runScanTransaction { tr ->
            tr.forEachInRangeBatch(FDBRange(nextStart, historyEnd), false) { historyEntry ->
                val historyKey = historyEntry.key
                val versionOffset = historyPrefix.size
                if (historyKey.size != versionOffset + VERSION_BYTE_SIZE + keySize) return@forEachInRangeBatch true
                val version = historyKey.readReversedVersionBytes(versionOffset)
                if (version < scanRequest.fromVersion) return@forEachInRangeBatch false
                val lowestSelectedVersion = matchingVersions.values.minOrNull()
                if (
                    matchingKeys.size.toUInt() >= scanRequest.limit &&
                    lowestSelectedVersion != null &&
                    version < lowestSelectedVersion
                ) return@forEachInRangeBatch false

                val keyOffset = versionOffset + VERSION_BYTE_SIZE
                var keyReadIndex = keyOffset
                val key = scanRequest.dataModel.key {
                    historyKey[keyReadIndex++]
                }
                if (!seenKeys.add(key)) return@forEachInRangeBatch true

                processEntry(tr, key, version, historyEntry.value.firstOrNull() == 1.toByte())
                true
            }
        }

        if (result.completed || result.stoppedByCallback) break
        nextStart = result.lastKey?.let { it + nextKeySuffix } ?: break
    }

    matchingKeys.sortWith(
        compareByDescending<Key<DM>> { matchingVersions[it] ?: 0uL }
            .thenByDescending { matchingOrder[it] ?: -1 }
    )
    if (matchingKeys.size.toUInt() > scanRequest.limit) {
        val keptKeys = matchingKeys.take(scanRequest.limit.toInt()).toHashSet()
        matchingKeys.subList(scanRequest.limit.toInt(), matchingKeys.size).clear()
        updates.removeAll { update ->
            when (update) {
                is AdditionUpdate<*> -> update.key !in keptKeys
                is ChangeUpdate<*> -> update.key !in keptKeys
                is RemovalUpdate<*> -> update.key !in keptKeys
                else -> false
            }
        }
    }

    runTransaction { tr ->
        scanRequest.orderedKeys?.let { orderedKeys ->
            val matchingKeysSet = matchingKeys.toHashSet()
            val orderedKeysSet = orderedKeys.toHashSet()

            orderedKeys.subtract(matchingKeysSet).forEach { removedKey ->
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

            matchingKeys.subtract(orderedKeysSet).forEach { addedKey ->
                val creationVersion = tr.readCreationVersion(tableDirs, addedKey.bytes, scanRequest.toVersion)
                if (creationVersion != null) {
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
                    )?.withSoftDeleteState(tr, tableDirs, addedKey.bytes, scanRequest.toVersion)?.let { valuesWithMeta ->
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

private fun ScanUpdatesRequest<*>.canUseUpdateHistoryIndex() =
    order == null && startKey == null && includeStart && fromVersion == 0uL && toVersion == null && maxVersions == 1u

private fun ScanUpdatesRequest<*>.needsSoftDeleteFallback() =
    toVersion == null && (maxVersions > 1u || !filterSoftDeleted)

private fun <DM : IsRootDataModel> ValuesWithMetaData<DM>.withSoftDeleteState(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: ByteArray,
    toVersion: ULong?
): ValuesWithMetaData<DM> {
    if (toVersion == null) {
        return this
    }

    val deleted = isSoftDeleted(tr, tableDirs, toVersion, key)
    return if (isDeleted == deleted) this else copy(isDeleted = deleted)
}

/** Simple getter to compute index values for current key within a single transaction */
private class StoreValuesGetter(
    private val tr: Transaction,
    private val tableDirs: IsTableDirectories,
    private val decryptValue: DecryptValue? = null
) : IsValuesGetter {
    private lateinit var keyBytes: ByteArray
    private val cache = HashMap<IsPropertyReference<*, *, *>, Any?>(8)

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
            tr.getValue(
                tableDirs,
                toVersion,
                keyBytes,
                propertyReference.toStorageByteArray(),
                decryptValue = decryptValue
            ) { valueBytes, offset, length ->
                valueBytes.convertToValue(propertyReference, offset, length) as T?
            }
        }

        cache[propertyReference] = value
        return value
    }
}

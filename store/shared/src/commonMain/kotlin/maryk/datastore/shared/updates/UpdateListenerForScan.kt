package maryk.datastore.shared.updates

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.toULong
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.findByteIndexAndSizeByPartIndex
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.ScanRange
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Key
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.IsIndexUpdate
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.ScanType.UpdateHistoryScan
import maryk.datastore.shared.orderToScanType
import maryk.datastore.shared.updates.Update.Change
import maryk.lib.extensions.compare.compareTo

/** Update listener for scans */
class UpdateListenerForScan<DM: IsRootDataModel, RP: IsDataResponse<DM>>(
    request: IsScanRequest<DM, RP>,
    val scanRange: KeyScanRanges,
    response: IsDataResponse<DM>
) : UpdateListener<DM, IsScanRequest<DM, RP>>(
    request,
    response
) {
    internal val usesUpdateHistoryIndex =
        response is UpdatesResponse<DM> && response.dataFetchType is FetchByUpdateHistoryIndex

    private val responseDataFetchType = when (response) {
        is UpdatesResponse<DM> -> response.dataFetchType
        is ValuesResponse<DM> -> response.dataFetchType
        is ChangesResponse<DM> -> response.dataFetchType
        else -> null
    }

    private val scanType = when {
        usesUpdateHistoryIndex -> UpdateHistoryScan()
        responseDataFetchType is FetchByIndexScan -> {
            val indexReference = responseDataFetchType.index
                ?: throw StorageException("Index scan response did not identify its index")
            val index = request.dataModel.Meta.indexes?.firstOrNull {
                it.referenceStorageByteArray.bytes.contentEquals(indexReference)
            } ?: throw StorageException("Index scan response refers to an unknown index")
            IndexScan(index, responseDataFetchType.direction)
        }
        responseDataFetchType is FetchByTableScan -> TableScan(responseDataFetchType.direction)
        else -> request.dataModel.orderToScanType(request.order, scanRange.equalPairs)
    }

    internal val usesTableScan get() = scanType is TableScan
    internal val usesIndexScan get() = scanType is IndexScan

    internal val indexScanRange = (scanType as? IndexScan)?.index?.createScanRange(request.where, scanRange)
    private val indexStartBoundary = when (response) {
        is UpdatesResponse<DM> -> (response.dataFetchType as? FetchByIndexScan)?.startKey
        is ValuesResponse<DM> -> (response.dataFetchType as? FetchByIndexScan)?.startKey
        is ChangesResponse<DM> -> (response.dataFetchType as? FetchByIndexScan)?.startKey
        else -> null
    }?.takeUnless { it.isEmpty() }

    // Source versions determine history order even when listener reads overtake
    // queued commits or independent writers commit in a different HLC order.
    private val historyVersions = mutableMapOf<Key<DM>, ULong>().apply {
        if (usesUpdateHistoryIndex && response is UpdatesResponse<DM>) {
            val ordered = response.updates.firstOrNull() as? OrderedKeysUpdate<DM>
            val versions = ordered?.sortingKeys
            if (versions != null) {
                if (versions.size != ordered.keys.size || versions.any { it.size != ULong.SIZE_BYTES }) {
                    throw StorageException("Update-history ordering metadata does not match its keys")
                }
                ordered.keys.forEachIndexed { index, key -> put(key, versions[index].bytes.toULong()) }
            } else {
                // Retain support for manually supplied responses from older callers.
                for (update in response.updates) {
                    val key = when (update) {
                        is AdditionUpdate<DM> -> update.key
                        is ChangeUpdate<DM> -> update.key
                        else -> null
                    }
                    if (key != null && key in matchingKeys.value) {
                        put(key, maxOf(get(key) ?: 0uL, update.version))
                    }
                }
            }
        }
    }

    internal val sortedValues: AtomicRef<List<ByteArray>>? = when(response) {
        is UpdatesResponse<DM> -> {
            if (usesUpdateHistoryIndex) null else (response.updates.firstOrNull() as? OrderedKeysUpdate<DM>)?.sortingKeys?.let { sortingKeys ->
                atomic(sortingKeys.map { it.bytes }.toList())
            }
        }
        is ValuesResponse<DM> -> {
            if (scanType is IndexScan) {
                response.values.mapNotNull { valuesWithMeta ->
                    resolveIndexKey(valuesWithMeta.values, valuesWithMeta.key.bytes)
                }.let(::atomic)
            } else null
        }
        is ChangesResponse<DM> -> {
            if (scanType is IndexScan) {
                response.changes.mapNotNull { it.sortingKey?.bytes }.let(::atomic)
            } else null
        }
        else -> throw IllegalArgumentException("Unknown response type $response. Cannot process")
    }

    override suspend fun process(
        update: Update<DM>,
        dataStore: IsDataStore
    ) {
        if (scanType is IndexScan) {
            update.process(this, dataStore, sendFlow)
            return
        }

        val updateKey = update.key.bytes
        val shouldProcess: Boolean = when (scanType) {
            is TableScan -> when (scanType.direction) {
                ASC -> !scanRange.keyBeforeStart(updateKey, 0)
                DESC -> !scanRange.keyAfterStart(updateKey, 0)
            }
            is UpdateHistoryScan -> !scanRange.keyBeforeStart(updateKey, 0)
        }

        when (scanType) {
            is UpdateHistoryScan -> {
                if (shouldProcess && scanRange.keyWithinRanges(updateKey, 0) && scanRange.matchesPartials(updateKey)) {
                    update.process(this, dataStore, sendFlow)
                }
            }
            else -> {
                if (shouldProcess && scanRange.keyWithinRanges(updateKey, 0) && scanRange.matchesPartials(updateKey)) {
                    update.process(this, dataStore, sendFlow)
                }
            }
        }
    }

    override fun addValues(key: Key<DM>, values: Values<DM>): Int? {
        // Find position and add it if necessary to order if it fits within limit
        return when (scanType) {
            is IndexScan -> {
                val indexKey = resolveIndexKey(values, key.bytes)
                    ?: return null

                findSortedKeyIndex(indexKey)?.let { indexPosition ->
                    when {
                        indexPosition < 0 -> {
                            val newPos = indexPosition * -1 - 1
                            val beforeStartKey = indexStartBoundary?.let { boundary ->
                                when (scanType.direction) {
                                    ASC -> indexKey compareTo boundary < 0
                                    DESC -> indexKey compareTo boundary > 0
                                }
                            } == true

                            if (!beforeStartKey && newPos.toUInt() < request.limit) {
                                sortedValues?.value = buildList {
                                    addAll(sortedValues.value)
                                    add(newPos, indexKey)
                                }

                                matchingKeys.value = buildList {
                                    addAll(matchingKeys.value)
                                    add(newPos, key)
                                }
                                newPos
                            } else {
                                null
                            }
                        }
                        else -> throw StorageException("Index unexpectedly already in sortedValues for $values")
                    }
                }
            }
            is TableScan -> {
                findKeyIndexForTableScan(key).let { indexPosition ->
                    when {
                        indexPosition < 0 -> {
                            val newPos = indexPosition * -1 - 1
                            if (newPos.toUInt() < request.limit) {
                                matchingKeys.value = buildList {
                                    addAll(matchingKeys.value)
                                    add(newPos, key)
                                }
                                newPos
                            } else {
                                null
                            }
                        }
                        // else already in list
                        else -> indexPosition
                    }
                }
            }
            is UpdateHistoryScan -> {
                val existingIndex = matchingKeys.value.indexOf(key)
                when {
                    existingIndex >= 0 -> existingIndex
                    request.limit == 0u -> null
                    else -> {
                        matchingKeys.value = listOf(key) + matchingKeys.value
                        0
                    }
                }
            }
        }
    }

    override fun addValues(key: Key<DM>, values: Values<DM>, version: ULong): Int? =
        if (usesUpdateHistoryIndex) addHistoryKey(key, version) else addValues(key, values)

    private fun addHistoryKey(key: Key<DM>, version: ULong): Int? {
        val currentKeys = matchingKeys.value.filterNot { it == key }
        val latestVersion = maxOf(historyVersions[key] ?: 0uL, version)
        // Match ScanUpdates' newest-first order, including deterministic ties
        // from a batch whose records share the same revision.
        val position = currentKeys.indexOfFirst {
            val otherVersion = historyVersions[it] ?: 0uL
            otherVersion < latestVersion ||
                otherVersion == latestVersion && (it.bytes compareTo key.bytes) < 0
        }
            .takeIf { it >= 0 } ?: currentKeys.size
        if (position.toUInt() >= request.limit) return null
        historyVersions[key] = latestVersion
        matchingKeys.value = currentKeys.toMutableList().apply { add(position, key) }
        return position
    }

    private fun findSortedKeyIndex(indexKey: ByteArray) =
        sortedValues?.value?.binarySearch {
            when (scanType.direction) {
                ASC -> it compareTo indexKey
                DESC -> indexKey compareTo it
            }
        }

    /** [values] at [key] are known to be at end of sorted range so add it specifically there */
    fun addValuesAtEnd(key: Key<DM>, values: Values<DM>) {
        val indexKey = if (scanType is IndexScan && sortedValues != null) {
            resolveIndexKey(values, key.bytes)
                ?: throw StorageException("Unexpected null at indexed value")
        } else null
        matchingKeys.value = matchingKeys.value + key
        indexKey?.let { sortedValues!!.value = sortedValues.value + it }
    }

    /** Return the actual insertion position when a refill read is newer than queued updates. */
    fun addValuesAtEnd(key: Key<DM>, values: Values<DM>, version: ULong): Int =
        addValues(key, values, version)
            ?: throw StorageException("Live scan refill did not fit the available result slot")

    internal fun resolveIndexKey(values: Values<DM>, keyBytes: ByteArray): ByteArray? {
        val indexScan = scanType as? IndexScan ?: return null

        val matchingIndexKeys = visibleIndexKeys(indexScan.index.toStorageByteArraysForIndex(values, keyBytes))

        if (matchingIndexKeys.isEmpty()) return null

        return when (scanType.direction) {
            ASC -> matchingIndexKeys.minWithOrNull { a, b -> a compareTo b }
            DESC -> matchingIndexKeys.maxWithOrNull { a, b -> a compareTo b }
        }
    }

    internal fun compareIndexKeys(first: ByteArray, second: ByteArray): Int = when ((scanType as IndexScan).direction) {
        ASC -> first compareTo second
        DESC -> second compareTo first
    }

    override fun removeKey(key: Key<DM>): Int {
        val index = super.removeKey(key)

        if (index >= 0) {
            historyVersions.remove(key)
            sortedValues?.value = sortedValues.value.filterIndexed { i, _ -> i != index }
        }
        return index
    }

    override suspend fun changeOrder(
        change: Change<DM>,
        changedHandler: suspend (Int?, Boolean) -> Unit
    ) = changeOrder(change, null, changedHandler)

    override suspend fun changeOrder(
        change: Change<DM>,
        currentValues: Values<DM>?,
        changedHandler: suspend (Int?, Boolean) -> Unit
    ) {
        when (scanType) {
            is TableScan -> {
                val index = findKeyIndexForTableScan(change.key)
                if (index >= 0) {
                    changedHandler(index, false)
                }
            }
            is UpdateHistoryScan -> {
                val existingIndex = matchingKeys.value.indexOf(change.key)
                val newIndex = addHistoryKey(change.key, change.version)
                changedHandler(newIndex, newIndex != existingIndex)
            }
            is IndexScan -> {
                // Should always exist since earlier was checked if matchingKeys contains this key
                val existingIndex = findKeyIndexForIndexScan(change.key)
                val existingIndexKey = if (existingIndex >= 0) {
                    sortedValues?.value?.getOrNull(existingIndex)
                } else {
                    null
                }

                val indexChange = change.changes.firstOrNull { it is IndexChange } as IndexChange?
                val relevantIndexChanges = indexChange
                    ?.changes
                    ?.filter { it.index == scanType.index.referenceStorageByteArray }
                    .orEmpty()

                // Check if there are index changes for index needed to order this request
                when {
                    relevantIndexChanges.isEmpty() -> { // Nothing changed
                        if (existingIndex >= 0) {
                            changedHandler(existingIndex, false)
                        }
                    }
                    else -> { // Was changed in order
                        val indexKey = currentValues?.let { resolveIndexKey(it, change.key.bytes) }
                            ?: resolveIndexKeyFromChanges(existingIndexKey, relevantIndexChanges)

                        // check if key is valid according to filters
                        if (indexKey != null) {
                            val index = findSortedKeyIndex(indexKey)

                            if (existingIndex == -1 || existingIndex != index) { // Is at new index
                                if (existingIndex >= 0) {
                                    matchingKeys.value = matchingKeys.value.filterIndexed { i, _ -> i != existingIndex }
                                    sortedValues?.value = sortedValues.value.filterIndexed { i, _ -> i != existingIndex }
                                }

                                if (index != null) {
                                    if (index < 0) {
                                        val newIndex = index * -1 - 1

                                        if (newIndex.toUInt() >= request.limit) {
                                            // Don't add items which are moved to after the limit
                                            changedHandler(null, false)
                                        } else {
                                            val beforeStartKey = indexStartBoundary?.let { boundary ->
                                                when (scanType.direction) {
                                                    ASC -> indexKey compareTo boundary < 0
                                                    DESC -> indexKey compareTo boundary > 0
                                                }
                                            } == true

                                            if (beforeStartKey) {
                                                // Remove items which are before the first start key/range
                                                changedHandler(null, false)
                                            } else {
                                                // correction if existing value delete made index to be off by one
                                                val correction = if (existingIndex != -1 && existingIndex < newIndex) 1 else 0
                                                val adjustedIndex = newIndex - correction

                                                matchingKeys.value = buildList {
                                                    addAll(matchingKeys.value)
                                                    add(adjustedIndex, change.key)
                                                }
                                                sortedValues?.value = buildList {
                                                    addAll(sortedValues.value)
                                                    add(adjustedIndex, indexKey)
                                                }

                                                changedHandler(adjustedIndex, true)
                                            }
                                        }
                                    } else {
                                        throw StorageException("Unexpected existing index for ${change.key} its sorted key {${indexKey.toHexString()} for changes ${change.changes}")
                                    }
                                } else { // removed
                                    changedHandler(null, false)
                                }
                            } else { // Is at same position as existing index
                                if (existingIndex >= 0) {
                                    if (existingIndexKey != null && !existingIndexKey.contentEquals(indexKey)) {
                                        sortedValues?.value = buildList {
                                            addAll(sortedValues.value)
                                            set(existingIndex, indexKey)
                                        }
                                    }
                                    changedHandler(existingIndex, false)
                                }
                            }
                        } else {
                            // Not matching key index conditions
                            changedHandler(null, false)
                        }
                    }
                }
            }
        }
    }

    private fun resolveIndexKeyFromChanges(
        existingIndexKey: ByteArray?,
        indexChanges: List<IsIndexUpdate>
    ): ByteArray? {
        val candidates = mutableListOf<ByteArray>()
        existingIndexKey?.let { candidates += it }

        indexChanges.forEach { indexChange ->
            when (indexChange) {
                is IndexDelete -> {
                    val toRemove = indexChange.indexKey.bytes
                    candidates.removeAll { it.contentEquals(toRemove) }
                }
                is IndexUpdate -> {
                    indexChange.previousIndexKey?.bytes?.let { previous ->
                        candidates.removeAll { it.contentEquals(previous) }
                    }
                    candidates += indexChange.indexKey.bytes
                }
            }
        }

        val matchingCandidates = visibleIndexKeys(candidates)

        if (matchingCandidates.isEmpty()) return null

        return when (scanType.direction) {
            ASC -> matchingCandidates.minWithOrNull { a, b -> a compareTo b }
            DESC -> matchingCandidates.maxWithOrNull { a, b -> a compareTo b }
        }
    }

    private fun visibleIndexKeys(indexKeys: List<ByteArray>): List<ByteArray> =
        indexKeys.filter { indexKey ->
            val matchesRange = indexScanRange?.let { range ->
                resolveIndexValueSize(indexKey)?.let { valueSize ->
                    range.matchesPartials(indexKey, length = valueSize) &&
                        range.ranges.any { scanRange ->
                            scanRange.matchesIndexValue(range, indexKey, valueSize)
                        }
                } == true
            } ?: true

            if (!matchesRange) {
                false
            } else {
                indexStartBoundary?.let { boundary ->
                    when (scanType.direction) {
                        ASC -> indexKey compareTo boundary >= 0
                        DESC -> indexKey compareTo boundary <= 0
                    }
                } ?: true
            }
        }

    private fun resolveIndexValueSize(indexKey: ByteArray): Int? {
        val indexScan = scanType as? IndexScan ?: return null
        if (indexKey.size < scanRange.keySize) return null

        return try {
            val (offset, size) = findByteIndexAndSizeByPartIndex(
                partIndex = indexScan.index.indexPartCount - 1,
                indexable = indexKey,
                keySize = scanRange.keySize,
                indexPartCount = indexScan.index.indexPartCount
            )
            offset + size
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Find the first matching key.
     */
    private fun findKeyIndexForTableScan(key: Key<DM>): Int {
        val comparator: (Key<DM>) -> Int = when (scanType.direction) {
            ASC -> {{ it compareTo key }}
            DESC -> {{ key compareTo it }}
        }
        return matchingKeys.value.binarySearch(comparison = comparator)
    }

    /**
     * Find the first matching key.
     * Mind: keys are not ordered so efficient binary search is not possible
     */
    private fun findKeyIndexForIndexScan(key: Key<DM>): Int {
        val predicate: (Key<DM>) -> Boolean = when (scanType.direction) {
            ASC -> {{ it compareTo key == 0 }}
            DESC -> {{ key compareTo it == 0 }}
        }
        return matchingKeys.value.indexOfFirst(predicate = predicate)
    }

    /** Get last key depending on scan direction */
    fun getLast() = when (scanType) {
        is UpdateHistoryScan -> matchingKeys.value.last()
        is IndexScan -> matchingKeys.value.last()
        else -> matchingKeys.value.last()
    }
}

private fun ScanRange.matchesIndexValue(indexScanRange: IndexableScanRanges, indexValue: ByteArray, valueSize: Int): Boolean {
    val rangeLength = indexRangeLength(indexScanRange, this, valueSize)
    return !keyBeforeStart(indexValue, length = rangeLength) && !keyOutOfRange(indexValue, length = rangeLength)
}

private fun indexRangeLength(indexScanRange: IndexableScanRanges, range: ScanRange, valueSize: Int): Int =
    indexScanRange.rangeLength(range, valueSize)

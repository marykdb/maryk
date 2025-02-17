package maryk.datastore.shared.updates

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Key
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
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
    private val scanType = request.dataModel.orderToScanType(request.order, scanRange.equalPairs)

    internal val indexScanRange = (scanType as? IndexScan)?.index?.createScanRange(request.where, scanRange)

    internal val sortedValues: AtomicRef<List<ByteArray>>? = when(response) {
        is UpdatesResponse<DM> -> {
            (response.updates.firstOrNull() as? OrderedKeysUpdate<DM>)?.sortingKeys?.let { sortingKeys ->
                atomic(sortingKeys.map { it.bytes }.toList())
            }
        }
        is ValuesResponse<DM> -> {
            if (scanType is IndexScan) {
                response.values.mapNotNull { valuesWithMeta ->
                    scanType.index.toStorageByteArrayForIndex(valuesWithMeta.values, valuesWithMeta.key.bytes)
                }.let(::atomic)
            } else null
        }
        is ChangesResponse<DM> -> {
            if (scanType is IndexScan) {
                response.changes.mapNotNull { it.sortingKey?.bytes }.let(::atomic)
            } else null
        }
        else -> throw Exception("Unknown response type $response. Cannot process")
    }

    override suspend fun process(
        update: Update<DM>,
        dataStore: IsDataStore
    ) {
        val shouldProcess: Boolean = when (scanType) {
            is TableScan -> when (scanType.direction) {
                ASC -> !scanRange.keyBeforeStart(update.key.bytes, 0)
                DESC -> !scanRange.keyAfterStart(update.key.bytes, 0)
            }
            is IndexScan -> true
        }

        if (shouldProcess && scanRange.keyWithinRanges(update.key.bytes, 0) && scanRange.matchesPartials(update.key.bytes)) {
            update.process(this, dataStore, sendFlow)
        }
    }

    override fun addValues(key: Key<DM>, values: Values<DM>): Int? {
        // Find position and add it if necessary to order if it fits within limit
        return when (scanType) {
            is IndexScan -> {
                val indexKey = scanType.index.toStorageByteArrayForIndex(values, key.bytes)
                    ?: return null

                findSortedKeyIndex(indexKey)?.let { indexPosition ->
                    when {
                        indexPosition < 0 -> {
                            val newPos = indexPosition * -1 - 1
                            // Only add when position is smaller than limit and after first key
                            if (newPos != 0 && newPos < request.limit.toInt()) {
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
                            matchingKeys.value = buildList {
                                addAll(matchingKeys.value)
                                add(newPos, key)
                            }
                            newPos
                        }
                        // else already in list
                        else -> indexPosition
                    }
                }
            }
        }
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
        matchingKeys.value = matchingKeys.value + key

        // Add sort key also to the end
        if (scanType is IndexScan && sortedValues != null) {
            scanType.index.toStorageByteArrayForIndex(values, key.bytes)?.also {
                sortedValues.value = sortedValues.value + it
            } ?: throw StorageException("Unexpected null at indexed value")
        }
    }

    override fun removeKey(key: Key<DM>): Int {
        val index = super.removeKey(key)

        if (index >= 0) {
            sortedValues?.value = sortedValues.value.filterIndexed { i, _ -> i != index }
        }
        return index
    }

    override suspend fun changeOrder(change: Change<DM>, changedHandler: suspend (Int?, Boolean) -> Unit) {
        when (scanType) {
            is TableScan -> {
                val index = findKeyIndexForTableScan(change.key)
                if (index >= 0) {
                    changedHandler(index, false)
                }
            }
            is IndexScan -> {
                // Should always exist since earlier was checked if matchingKeys contains this key
                val existingIndex = findKeyIndexForIndexScan(change.key)

                val indexChange = change.changes.firstOrNull { it is IndexChange } as IndexChange?

                // Check if there are index changes for index needed to order this request
                when(val indexUpdate = indexChange?.changes?.firstOrNull { it.index == scanType.index.referenceStorageByteArray }) {
                    null -> { // Nothing changed
                        if (existingIndex >= 0) {
                            changedHandler(existingIndex, false)
                        }
                    }
                    is IndexDelete -> { // Was deleted from index
                        if (existingIndex >= 0) {
                            changedHandler(null, false)
                        }
                    }
                    is IndexUpdate -> { // Was changed in order
                        // check if key is valid according to filters
                        if (
                            indexScanRange!!.keyWithinRanges(indexUpdate.indexKey.bytes)
                            && indexScanRange.matchesPartials(indexUpdate.indexKey.bytes)
                        ) {
                            val index = findSortedKeyIndex(indexUpdate.indexKey.bytes)

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
                                        } else if (newIndex == 0 && indexScanRange.ranges.firstOrNull()?.keyBeforeStart(change.key.bytes) == true) {
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
                                                add(adjustedIndex, indexUpdate.indexKey.bytes)
                                            }

                                            changedHandler(adjustedIndex, true)
                                        }
                                    } else {
                                        throw StorageException("Unexpected existing index for ${change.key} its sorted key {${indexUpdate.indexKey.toHex()} for changes ${change.changes}")
                                    }
                                } else { // removed
                                    changedHandler(null, false)
                                }
                            } else { // Is at same position as existing index
                                if (existingIndex >= 0) {
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
    fun getLast() = when (scanType.direction) {
        ASC -> matchingKeys.value.last()
        DESC -> matchingKeys.value.first()
    }
}

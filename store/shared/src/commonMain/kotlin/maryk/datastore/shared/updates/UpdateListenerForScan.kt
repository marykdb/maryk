package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.values.Values
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.orderToScanType
import maryk.datastore.shared.updates.IsIndexUpdate.IndexChange
import maryk.datastore.shared.updates.IsIndexUpdate.IndexDelete
import maryk.datastore.shared.updates.Update.Change
import maryk.lib.extensions.compare.compareTo

/** Update listener for scans */
class UpdateListenerForScan<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    request: ScanChangesRequest<DM, P>,
    val scanRange: KeyScanRanges,
    scanResponse: ValuesResponse<DM, P>,
    sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) : UpdateListener<DM, P, ScanChangesRequest<DM, P>>(
    request,
    scanResponse.values.map { it.key }.toMutableList(),
    sendChannel
) {
    private val scanType = request.dataModel.orderToScanType(request.order, scanRange.equalPairs)

    private val sortedValues = (scanType as? IndexScan)?.let {
        scanResponse.values.map {
            scanType.index.toStorageByteArrayForIndex(it.values, it.key.bytes)
                ?: throw StorageException("Unexpected null value")
        }.toMutableList()
    }

    override suspend fun process(
        update: Update<DM, P>,
        dataStore: AbstractDataStore
    ) {
        val shouldProcess: Boolean = when (scanType) {
            is TableScan -> when (scanType.direction) {
                ASC -> !scanRange.keyBeforeStart(update.key.bytes, 0)
                DESC -> !scanRange.keyAfterStart(update.key.bytes, 0)
            }
            is IndexScan -> true
        }

        if (shouldProcess && scanRange.keyWithinRanges(update.key.bytes, 0) && scanRange.matchesPartials(update.key.bytes)) {
            update.process(this, dataStore, sendChannel)
        }
    }

    override fun addValues(key: Key<DM>, values: Values<DM, P>): Int? {
        // Find position and add it if necessary to order if it fits within limit
        return when (scanType) {
            is IndexScan -> {
                val indexKey = scanType.index.toStorageByteArrayForIndex(values, key.bytes)
                    ?: return null

                findSortedKeyIndex(indexKey)?.let { indexPosition ->
                    when {
                        indexPosition < 0 -> {
                            val newPos = indexPosition * -1 - 1
                            // Only add when position is smaller than limit
                            if (newPos < request.limit.toInt()) {
                                sortedValues?.add(newPos, indexKey)
                                matchingKeys.add(newPos, key)
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
                findKeyIndex(key).let { indexPosition ->
                    when {
                        indexPosition < 0 -> {
                            val newPos = indexPosition * -1 - 1
                            matchingKeys.add(newPos, key)
                            newPos
                        }
                        // else already in list
                        else -> indexPosition
                    }
                }
            }
        }
    }

    private fun findSortedKeyIndex(indexKey: ByteArray): Int? {
        return sortedValues?.binarySearch {
            when (scanType.direction) {
                ASC -> it.compareTo(indexKey)
                DESC -> indexKey.compareTo(it)
            }
        }
    }

    /** [values] at [key] are known to be at end of sorted range so add it specifically there */
    fun addValuesAtEnd(key: Key<DM>, values: Values<DM, P>) {
        matchingKeys += key

        // Add sort key also to the end
        if (scanType is IndexScan && sortedValues != null) {
            scanType.index.toStorageByteArrayForIndex(values, key.bytes)?.also {
                sortedValues.add(it)
            } ?: throw StorageException("Unexpected null at indexed value")
        }
    }

    override fun removeKey(key: Key<DM>): Int {
        val index = super.removeKey(key)

        if (index >= 0) {
            sortedValues?.removeAt(index)
        }
        return index
    }

    override suspend fun changeOrder(change: Change<DM, P>, changedHandler: suspend (Int?) -> Unit) {
        when (scanType) {
            is TableScan -> {
                val index = findKeyIndex(change.key)
                if (index >= 0) {
                    changedHandler(index)
                }
            }
            is IndexScan -> {
                val existingIndex = findKeyIndex(change.key)

                when(val indexUpdate = change.indexUpdates?.firstOrNull { it.index == scanType.index }) {
                    null -> { // Nothing changed
                        if (existingIndex >= 0) {
                            changedHandler(existingIndex)
                        }
                    }
                    is IndexDelete -> { // Was deleted from index
                        if (existingIndex >= 0) {
                            changedHandler(null)
                        }
                    }
                    is IndexChange -> { // Was changed in order
                        val index = findSortedKeyIndex(indexUpdate.indexKey)

                        if (existingIndex != index) { // Is at new index
                            matchingKeys.removeAt(existingIndex)
                            sortedValues?.removeAt(existingIndex)

                            if (index != null) {
                                if (index == -1) {
                                    val newIndex = index * 1 - 1
                                    matchingKeys.add(newIndex, change.key)
                                    sortedValues?.add(newIndex, indexUpdate.indexKey)

                                    changedHandler(newIndex)
                                } else {
                                    throw StorageException("Unexpected existing index for $change.key its sorted key")
                                }
                            } else { // removed
                                changedHandler(null)
                            }
                        } else { // Is at same position as existing index
                            if (existingIndex >= 0) {
                                changedHandler(existingIndex)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Find the first matching key.
     * Mind: keys are not ordered so efficient binary search is not possible
     */
    private fun findKeyIndex(key: Key<DM>): Int {
        val predicate: (Key<DM>) -> Boolean = when (scanType.direction) {
            ASC -> {{ it.compareTo(key) == 0 }}
            DESC -> {{ key.compareTo(it) == 0 }}
        }
        return matchingKeys.indexOfFirst(predicate = predicate)
    }
}

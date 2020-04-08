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
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.orderToScanType
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.toHex

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
    val scanType = request.dataModel.orderToScanType(request.order, scanRange.equalPairs)

    internal val sortedValues = (scanType as? IndexScan)?.let {
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

                sortedValues?.binarySearch {
                    when (scanType.direction) {
                        ASC -> it.compareTo(indexKey)
                        DESC -> indexKey.compareTo(it)
                    }
                }?.let { indexPosition ->
                    when {
                        indexPosition < 0 -> {
                            println("§ $indexPosition ${matchingKeys.joinToString { it.toHex() }} ${sortedValues.joinToString { it.toHex() }}")
                            val newPos = indexPosition * -1 - 1
                            // Only add when position is smaller than limit
                            if (newPos < request.limit.toInt()) {
                                sortedValues.add(newPos, indexKey)
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
                matchingKeys.binarySearch {
                    when (scanType.direction) {
                        ASC -> it.compareTo(key)
                        DESC -> key.compareTo(it)
                    }
                }.let { indexPosition ->
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
}

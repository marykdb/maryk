package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.lib.extensions.compare.compareTo
import kotlin.math.min

internal fun <DM : IsRootDataModel> scanStore(
    dataStore: DataStore<DM>,
    scanRequest: IsScanRequest<DM, *>,
    recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?,
    direction: Direction,
    scanRange: KeyScanRanges,
    processStoreValue: (DataRecord<DM>, ByteArray?) -> Unit
) {
    val toVersion = scanRequest.toVersion?.let { HLC(it) }

    when (direction) {
        ASC -> {
            for (range in scanRange.ranges) {
                val startKey = range.getAscendingStartKey(scanRange.startKey, scanRange.includeStart)

                val startIndex = dataStore.records.binarySearch {
                    it.key.bytes compareTo startKey
                }.let { index ->
                    when {
                        index < 0 -> index * -1 - 1 // If negative start and thus not found at first entry point
                        !range.startInclusive -> index + 1 // Skip the match if not inclusive
                        else -> index
                    }
                }

                var currentSize = 0u

                for (index in startIndex until dataStore.records.size) {
                    val record = dataStore.records[index]

                    if (range.keyOutOfRange(record.key.bytes)) {
                        break
                    }

                    if (!scanRange.matchesPartials(record.key.bytes)) {
                        continue
                    }

                    if (scanRequest.shouldBeFiltered(record, toVersion, recordFetcher)) {
                        continue
                    }

                    processStoreValue(record, null)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break
                }
            }
        }
        DESC -> {
            for (range in scanRange.ranges.reversed()) {
                val lastKey = range.getDescendingStartKey(scanRange.startKey, scanRange.includeStart)

                val startIndex = lastKey?.let { endRange ->
                    dataStore.records.binarySearch { it.key.bytes compareTo endRange }.let { index ->
                        when {
                            index < 0 -> index * -1 - 1 // If negative start at first entry point
                            else -> index
                        }
                    }
                } ?: dataStore.records.lastIndex

                var currentSize = 0u

                for (index in min(startIndex, dataStore.records.lastIndex) downTo 0) {
                    val record = dataStore.records[index]

                    if (range.keyBeforeStart(record.key.bytes)) {
                        break
                    }

                    if (!scanRange.matchesPartials(record.key.bytes)) {
                        continue
                    }

                    if (scanRequest.shouldBeFiltered(record, toVersion, recordFetcher)) {
                        continue
                    }

                    processStoreValue(record, null)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break
                }
            }
        }
    }
}

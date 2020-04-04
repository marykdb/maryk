package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.ScanType.IndexScan
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.lib.extensions.toHex
import kotlin.math.min

internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> scanIndex(
    dataStore: DataStore<DM, P>,
    scanRequest: IsScanRequest<DM, P, *>,
    recordFetcher: (IsRootValuesDataModel<*>, Key<*>) -> DataRecord<*, *>?,
    indexScan: IndexScan,
    keyScanRange: KeyScanRanges,
    processStoreValue: (DataRecord<DM, P>) -> Unit
) {
    val indexReference = indexScan.index.toReferenceStorageByteArray()
    val index = dataStore.getOrCreateIndex(indexReference)

    val startKey = scanRequest.startKey?.let { startKey ->
        recordFetcher(scanRequest.dataModel, scanRequest.startKey as Key<*>)?.let { startRecord ->
            val correctedStartKey = if (scanRequest.includeStart) startKey.bytes else startKey.bytes.nextByteInSameLength()
            indexScan.index.toStorageByteArrayForIndex(startRecord, correctedStartKey)
        }
    }

    val indexScanRange = indexScan.index.createScanRange(scanRequest.where, keyScanRange, startKey)

    val toVersion = scanRequest.toVersion?.let { HLC(it) }

    when (indexScan.direction) {
        ASC -> {
            for (indexRange in indexScanRange.ranges) {
                val startIndex = indexRange.start.let { startRange ->
                    val startRangeToSearch = if (indexRange.startInclusive) {
                        startRange
                    } else {
                        // Go past start range if not inclusive.
                        startRange.nextByteInSameLength()
                    }

                    if (!indexRange.startInclusive && startRangeToSearch === startRange) {
                        // If start range was not highered it was not possible so scan to lastIndex
                        index.records.lastIndex
                    } else {
                        index.records.binarySearch { it.value.compareTo(startRangeToSearch) }.let { valueIndex ->
                            when {
                                valueIndex < 0 -> valueIndex * -1 - 1 // If negative start at first entry point
                                !indexRange.startInclusive -> valueIndex + 1 // Skip the match if not inclusive
                                else -> valueIndex
                            }
                        }
                    }
                }

                var currentSize: UInt = 0u

                for (i in startIndex until index.records.size) {
                    val indexRecord = index.records[i]
                    val dataRecord = indexRecord.record ?: continue

                    if (indexRange.keyOutOfRange(indexRecord.value)) {
                        break
                    }

                    if (!indexScanRange.matchesPartials(indexRecord.value)) {
                        continue
                    }

                    if (scanRequest.shouldBeFiltered(dataRecord, toVersion, recordFetcher)) {
                        continue
                    }

                    processStoreValue(dataRecord)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break
                }
            }
        }
        DESC -> {
            for (indexRange in indexScanRange.ranges.reversed()) {
                val startIndex = indexRange.end?.let { endRange ->
                    if (endRange.isEmpty()) {
                        index.records.lastIndex
                    } else {
                        val endRangeToSearch = if (indexRange.endInclusive) {
                            endRange.nextByteInSameLength()
                        } else {
                            endRange
                        }

                        if (indexRange.endInclusive && endRangeToSearch === endRange) {
                            // If was not highered it was not possible so scan to lastIndex
                            index.records.lastIndex
                        } else {
                            index.records.binarySearch { it.value.compareTo(endRangeToSearch) }.let { valueIndex ->
                                when {
                                    valueIndex < 0 -> valueIndex * -1 - 1 // If negative start at first entry point
                                    !indexRange.endInclusive -> valueIndex - 1 // Skip the match if not inclusive
                                    else -> valueIndex
                                }
                            }
                        }
                    }
                } ?: index.records.lastIndex

                var currentSize: UInt = 0u

                for (i in min(startIndex, index.records.lastIndex) downTo 0) {
                    val indexRecord = index.records[i]
                    val dataRecord = indexRecord.record ?: continue

                    if (indexRange.keyBeforeStart(indexRecord.value)) {
                        break
                    }

                    if (!indexScanRange.matchesPartials(indexRecord.value)) {
                        continue
                    }

                    if (scanRequest.shouldBeFiltered(dataRecord, toVersion, recordFetcher)) {
                        continue
                    }

                    processStoreValue(dataRecord)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break
                }
            }
        }
    }
}

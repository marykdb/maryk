package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.KeyScanRange
import maryk.core.processors.datastore.ScanType.IndexScan
import maryk.core.processors.datastore.createScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.lib.extensions.compare.compareTo
import kotlin.math.min

internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> scanIndex(
    dataStore: DataStore<DM, P>,
    scanRequest: IsScanRequest<DM, P, *>,
    indexScan: IndexScan,
    keyScanRange: KeyScanRange,
    processStoreValue: (DataRecord<DM, P>) -> Unit
) {
    val indexReference = indexScan.index.toReferenceStorageByteArray()
    val index = dataStore.getOrCreateIndex(indexReference)

    val indexScanRange = indexScan.index.createScanRange(scanRequest.filter, scanRequest.dataModel.keyByteSize)

    when (indexScan.direction) {
        ASC -> {
            val startIndex = index.records.binarySearch { it.value.compareTo(indexScanRange.start) }.let { valueIndex ->
                when {
                    valueIndex < 0 -> valueIndex * -1 - 1 // If negative start at first entry point
//                    !indexScanRange.startInclusive -> valueIndex + 1 // Skip the match if not inclusive
                    else -> valueIndex
                }
            }

            var currentSize: UInt = 0u

            for (i in startIndex until index.records.size) {
                val indexRecord = index.records[i]
                val dataRecord = indexRecord.record ?: continue

                if (indexScanRange.keyOutOfRange(indexRecord.value)) {
                    break
                }

                if (!indexScanRange.keyMatches(indexRecord.value)) {
                    continue
                }

                if (scanRequest.filterData(dataRecord, scanRequest.toVersion)) {
                    continue
                }

                processStoreValue(dataRecord)

                // Break when limit is found
                if (++currentSize == scanRequest.limit) break
            }
        }
        DESC -> {
            val startIndex = keyScanRange.end?.let { endRange ->
                index.records.binarySearch { it.value.compareTo(endRange) }.let { valueIndex ->
                    when {
                        valueIndex < 0 -> valueIndex * -1 - 1 // If negative start at first entry point
//                        !indexScanRange.endInclusive -> valueIndex - 1 // Skip the match if not inclusive
                        else -> valueIndex
                    }
                }
            } ?: index.records.lastIndex

            var currentSize: UInt = 0u

            for (i in min(startIndex, index.records.lastIndex) downTo 0) {
                val indexRecord = index.records[i]
                val dataRecord = indexRecord.record ?: continue

                if (indexScanRange.keyBeforeStart(indexRecord.value)) {
                    break
                }

                if (!indexScanRange.keyMatches(indexRecord.value)) {
                    continue
                }

                if (scanRequest.filterData(dataRecord, scanRequest.toVersion)) {
                    continue
                }

                processStoreValue(dataRecord)

                // Break when limit is found
                if (++currentSize == scanRequest.limit) break
            }
        }
    }
}

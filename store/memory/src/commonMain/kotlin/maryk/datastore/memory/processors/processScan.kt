package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.optimizeTableScan
import maryk.datastore.shared.orderToScanType

/** Walk with [scanRequest] on [dataStore] and do [processRecord] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processScan(
    scanRequest: IsScanRequest<DM, P, *>,
    dataStore: DataStore<DM, P>,
    processRecord: (DataRecord<DM, P>) -> Unit
) {
    val scanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes)

    scanRequest.checkToVersion(dataStore.keepAllVersions)

    when {
        // If hard key match then quit with direct record
        scanRange.isSingleKey() ->
            dataStore.getByKey(scanRange.ranges.first().start)?.let {
                if (shouldProcessRecord(it, scanRequest, scanRange)) {
                    processRecord(it)
                }
            }
        else -> {
            // Process uniques as a fast path
            scanRange.uniques?.let {
                if (it.isNotEmpty()) {
                    // Only process the first unique
                    val firstReference = it.first()

                    val uniqueIndex = dataStore.getOrCreateUniqueIndex(firstReference.reference)
                    @Suppress("UNCHECKED_CAST")
                    val value = firstReference.value as Comparable<Any>

                    val record = scanRequest.toVersion?.let { version ->
                        uniqueIndex[value, HLC(version)]
                    } ?: uniqueIndex[value]

                    record?.let {
                        if (shouldProcessRecord(record, scanRequest, scanRange)) {
                            processRecord(record)
                        }
                    }
                    return
                }
            }

            val scanIndex = scanRequest.dataModel.orderToScanType(scanRequest.order, scanRange.equalPairs)

            val processedScanIndex = if (scanIndex is TableScan) {
                scanRequest.dataModel.optimizeTableScan(scanIndex, scanRange.equalPairs)
            } else scanIndex

            when (processedScanIndex) {
                is TableScan -> {
                    scanStore(
                        dataStore,
                        scanRequest,
                        processedScanIndex.direction,
                        scanRange,
                        processRecord
                    )
                }
                is IndexScan -> {
                    scanIndex(
                        dataStore,
                        scanRequest,
                        processedScanIndex,
                        scanRange,
                        processRecord
                    )
                }
            }
        }
    }
}

internal fun <DM: IsRootValuesDataModel<P>, P:PropertyDefinitions> shouldProcessRecord(
    record: DataRecord<DM, P>,
    scanRequest: IsScanRequest<DM, P, *>,
    scanRange: KeyScanRanges
): Boolean {
    if (!scanRange.keyWithinRanges(record.key.bytes, 0)) {
        return false
    } else if (!scanRange.matchesPartials(record.key.bytes)) {
        return false
    }

    return !scanRequest.shouldBeFiltered(record, scanRequest.toVersion?.let { HLC(it) })
}

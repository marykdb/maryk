package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.optimizeTableScan
import maryk.datastore.shared.orderToScanType

/** Walk with [scanRequest] on [dataStore] and do [processRecord] */
internal fun <DM : IsRootDataModel> processScan(
    scanRequest: IsScanRequest<DM, *>,
    dataStore: DataStore<DM>,
    recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?,
    scanSetup: ((ScanType) -> Unit)? = null,
    processRecord: (DataRecord<DM>, ByteArray?) -> Unit
) {
    val keyScanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes, scanRequest.includeStart)

    scanRequest.checkToVersion(dataStore.keepAllVersions)

    when {
        // If hard key match then quit with direct record
        keyScanRange.isSingleKey() ->
            dataStore.getByKey(keyScanRange.ranges.first().start)?.let {
                if (shouldProcessRecord(it, scanRequest, keyScanRange, recordFetcher)) {
                    processRecord(it, null)
                }
            }
        else -> {
            // Process uniques as a fast path
            keyScanRange.uniques?.let {
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
                        if (shouldProcessRecord(record, scanRequest, keyScanRange, recordFetcher)) {
                            processRecord(record, null)
                        }
                    }
                    return
                }
            }

            val scanIndex = scanRequest.dataModel.orderToScanType(scanRequest.order, keyScanRange.equalPairs)

            val processedScanIndex = if (scanIndex is TableScan) {
                scanRequest.dataModel.optimizeTableScan(scanIndex, keyScanRange.equalPairs)
            } else scanIndex

            scanSetup?.invoke(processedScanIndex)

            when (processedScanIndex) {
                is TableScan -> {
                    scanStore(
                        dataStore,
                        scanRequest,
                        recordFetcher,
                        processedScanIndex.direction,
                        keyScanRange,
                        processRecord
                    )
                }
                is IndexScan -> {
                    scanIndex(
                        dataStore,
                        scanRequest,
                        recordFetcher,
                        processedScanIndex,
                        keyScanRange,
                        processRecord
                    )
                }
            }
        }
    }
}

internal fun <DM: IsRootDataModel> shouldProcessRecord(
    record: DataRecord<DM>,
    scanRequest: IsScanRequest<DM, *>,
    scanRange: KeyScanRanges,
    recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?
): Boolean {
    if (scanRange.keyBeforeStart(record.key.bytes, 0)
        || !scanRange.keyWithinRanges(record.key.bytes, 0)
        || !scanRange.matchesPartials(record.key.bytes)
    ) {
        return false
    }

    return !scanRequest.shouldBeFiltered(record, scanRequest.toVersion?.let { HLC(it) }, recordFetcher)
}

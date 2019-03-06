package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.ScanType.IndexScan
import maryk.core.processors.datastore.ScanType.TableScan
import maryk.core.processors.datastore.createScanRange
import maryk.core.processors.datastore.orderToScanType
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.lib.extensions.compare.matches

/** Walk with [scanRequest] on [dataStore] and do [processRecord] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processScan(
    scanRequest: IsScanRequest<DM, P, *>,
    dataStore: DataStore<DM, P>,
    processRecord: (DataRecord<DM, P>) -> Unit
) {
    val scanRange = scanRequest.dataModel.createScanRange(scanRequest.filter, scanRequest.startKey?.bytes)

    when {
        // If hard key match then quit with direct record
        scanRange.end?.matches(scanRange.start) == true && (scanRange.startInclusive || scanRange.endInclusive) ->
            dataStore.getByKey(scanRange.start)?.let {
                processRecord(it)
            }
        else -> {
            val scanIndex = scanRequest.dataModel.orderToScanType(scanRequest.order, scanRange.equalPairs)

            when (scanIndex) {
                is TableScan -> {
                    scanStore(
                        dataStore,
                        scanRequest,
                        scanIndex.direction,
                        scanRange,
                        processRecord
                    )
                }
                is IndexScan -> {

                }
            }
        }
    }
}

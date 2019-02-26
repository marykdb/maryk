package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.ScanType.IndexScan
import maryk.core.processors.datastore.ScanType.StoreScan
import maryk.core.processors.datastore.createScanRange
import maryk.core.processors.datastore.orderToScanType
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ScanRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.memory.StoreAction
import maryk.datastore.memory.records.DataStore

internal typealias ScanStoreAction<DM, P> = StoreAction<DM, P, ScanRequest<DM, P>, ValuesResponse<DM, P>>
internal typealias AnyScanStoreAction = ScanStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ScanRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processScanRequest(
    storeAction: ScanStoreAction<DM, P>,
    dataStore: DataStore<DM, P>
) {
    val scanRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM, P>>()

    val scanRange = scanRequest.dataModel.createScanRange(scanRequest.filter, scanRequest.startKey?.bytes)
    val scanIndex = orderToScanType(storeAction.request.order)

    when (scanIndex) {
        is StoreScan -> {
            scanStore(
                dataStore,
                scanIndex.direction,
                scanRange,
                scanRequest
            ) { record ->
                scanRequest.dataModel.recordToValueWithMeta(
                    scanRequest.select,
                    scanRequest.toVersion,
                    record
                )?.let {
                    // Only add if not null
                    valuesWithMeta += it
                }
            }
        }
        is IndexScan -> {

        }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = scanRequest.dataModel,
            values = valuesWithMeta
        )
    )
}


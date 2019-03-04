package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.ScanType.IndexScan
import maryk.core.processors.datastore.ScanType.TableScan
import maryk.core.processors.datastore.createScanRange
import maryk.core.processors.datastore.orderToScanType
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.memory.StoreAction
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.lib.extensions.compare.matches

internal typealias ScanChangesStoreAction<DM, P> = StoreAction<DM, P, ScanChangesRequest<DM, P>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ScanRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM, P>,
    dataStore: DataStore<DM, P>
) {
    val scanRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()

    val scanRange = scanRequest.dataModel.createScanRange(scanRequest.filter, scanRequest.startKey?.bytes)

    when {
        // If hard key match then quit with direct record
        scanRange.end?.matches(scanRange.start) == true && (scanRange.startInclusive || scanRange.endInclusive) ->
            dataStore.getByKey(scanRange.start)?.let {
                recordToObjectChanges(scanRequest, it, objectChanges)
            }
        else -> {
            val scanIndex = scanRequest.dataModel.orderToScanType(storeAction.request.order, scanRange.equalPairs)

            when (scanIndex) {
                is TableScan -> {
                    scanStore(
                        dataStore,
                        scanIndex.direction,
                        scanRange,
                        scanRequest
                    ) { record ->
                        recordToObjectChanges(scanRequest, record, objectChanges)
                    }
                }
                is IndexScan -> {

                }
            }
        }
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = scanRequest.dataModel,
            changes = objectChanges
        )
    )
}

private fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> recordToObjectChanges(
    scanRequest: ScanChangesRequest<DM, P>,
    record: DataRecord<DM, P>,
    objectChanges: MutableList<DataObjectVersionedChange<DM>>
) {
    scanRequest.dataModel.recordToObjectChanges(
        scanRequest.select,
        scanRequest.fromVersion,
        scanRequest.toVersion,
        record
    )?.let {
        // Only add if not null
        objectChanges += it
    }
}

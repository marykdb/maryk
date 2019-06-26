package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction

internal typealias ScanChangesStoreAction<DM, P> = StoreAction<DM, P, ScanChangesRequest<DM, P>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ScanChangesRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM, P>,
    dataStore: DataStore<DM, P>
) {
    val scanRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()

    processScan(scanRequest, dataStore) { record ->
        recordToObjectChanges(scanRequest, record, objectChanges)
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

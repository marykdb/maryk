package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions

internal typealias ScanChangesStoreAction<DM> = StoreAction<DM, ScanChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootDataModel>

/** Processes a ScanChangesRequest in a [storeAction] into a dataStore from [dataStoreFetcher] */
internal fun <DM : IsRootDataModel> processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<DM>
) {
    val scanRequest = storeAction.request
    val objectChanges = ArrayList<DataObjectVersionedChange<DM>>(scanRequest.limit.toInt().coerceAtLeast(4))

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher, scanRequest.toVersion?.let(::HLC))

    val dataStore = dataStoreFetcher.invoke(scanRequest.dataModel)

    scanRequest.checkMaxVersions(dataStore.keepAllVersions)

    val dataFetchType = processScan(
        scanRequest = scanRequest,
        dataStore = dataStore,
        recordFetcher = recordFetcher,
        allowTableScanOverride = true
    ) { record, sortingKey ->
        scanRequest.dataModel.recordHistoryToVersionedChanges(
            select = scanRequest.select,
            fromVersion = scanRequest.fromVersion,
            toVersion = scanRequest.toVersion,
            maxVersions = scanRequest.maxVersions,
            sortingKey = sortingKey,
            historyRecords = dataStore.getRecordHistoryByKey(record.key.bytes, scanRequest.toVersion?.let(::HLC))
        ).map { it.versionedChange }.takeIf { it.isNotEmpty() }?.let {
            objectChanges += DataObjectVersionedChange(record.key, sortingKey?.let(::Bytes), it)
        }
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = scanRequest.dataModel,
            changes = objectChanges,
            dataFetchType = dataFetchType,
        )
    )
}

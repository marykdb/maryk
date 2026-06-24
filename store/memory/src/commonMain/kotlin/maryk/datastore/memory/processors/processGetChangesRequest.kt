package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.FetchByKey
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion

internal typealias GetChangesStoreAction<DM> = StoreAction<DM, GetChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyGetChangesStoreAction = GetChangesStoreAction<IsRootDataModel>

/** Processes a GetChangesRequest in a [storeAction] into a dataStore from [dataStoreFetcher] */
internal fun <DM : IsRootDataModel> processGetChangesRequest(
    storeAction: GetChangesStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<DM>
) {
    val getRequest = storeAction.request
    val objectChanges = ArrayList<DataObjectVersionedChange<DM>>(getRequest.keys.size.coerceAtLeast(4))

    val dataStore = dataStoreFetcher.invoke(getRequest.dataModel)

    getRequest.checkToVersion(dataStore.keepAllVersions)
    getRequest.checkMaxVersions(dataStore.keepAllVersions)
    val toVersion = getRequest.toVersion?.let { HLC(it) }
    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher, toVersion)

    for (key in getRequest.keys) {
        val records = dataStore.getRecordHistoryByKey(key.bytes, toVersion)
        val record = records.lastOrNull() ?: continue

        if (getRequest.shouldBeFiltered(record, toVersion, recordFetcher)) {
            continue
        }

        getRequest.dataModel.recordHistoryToVersionedChanges(
            select = getRequest.select,
            fromVersion = getRequest.fromVersion,
            toVersion = getRequest.toVersion,
            maxVersions = getRequest.maxVersions,
            sortingKey = null,
            historyRecords = records
        ).map { it.versionedChange }.takeIf { it.isNotEmpty() }?.let {
            objectChanges += DataObjectVersionedChange(key, changes = it)
        }
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = getRequest.dataModel,
            changes = objectChanges,
            dataFetchType = FetchByKey,
        )
    )
}

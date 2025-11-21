package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.shared.StoreAction

internal typealias ChangeStoreAction<DM> = StoreAction<DM, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootDataModel>

/** Processes a ChangeRequest in a [storeAction] into a [FoundationDBDataStore] */
internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processChangeRequest(
    version: HLC,
    storeAction: ChangeStoreAction<DM>,
) {
    val changeRequest = storeAction.request

    val statuses = mutableListOf<IsChangeResponseStatus<DM>>()

    if (changeRequest.objects.isNotEmpty()) {
        val dbIndex = getDataModelId(changeRequest.dataModel)
        val tableDirs = getTableDirs(dbIndex)

        withContext(Dispatchers.IO) {
            for (objectChange in changeRequest.objects) {
                val status = processChange(
                    dataModel = changeRequest.dataModel,
                    key = objectChange.key,
                    lastVersion = objectChange.lastVersion,
                    changes = objectChange.changes,
                    version = version,
                    tableDirs = tableDirs,
                )
                statuses += status
            }
        }
    }

    storeAction.response.complete(
        ChangeResponse(
            changeRequest.dataModel,
            statuses
        )
    )
}

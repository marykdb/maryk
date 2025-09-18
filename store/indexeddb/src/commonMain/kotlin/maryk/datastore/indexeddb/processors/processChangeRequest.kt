package maryk.datastore.indexeddb.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.datastore.indexeddb.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias ChangeStoreAction<DM> = StoreAction<DM, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootDataModel>

/** Processes a ChangeRequest in a [storeAction] into a data store from [dataStoreFetcher] */
internal suspend fun <DM : IsRootDataModel> processChangeRequest(
    version: HLC,
    storeAction: ChangeStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<DM>,
    updateFlow: MutableSharedFlow<IsUpdateAction>
) {
    val changeRequest = storeAction.request

    val dataStore = dataStoreFetcher.invoke(changeRequest.dataModel)

    val statuses = mutableListOf<IsChangeResponseStatus<DM>>()

    if (changeRequest.objects.isNotEmpty()) {
        for (objectChange in changeRequest.objects) {
            statuses.add(
                processChange(
                    dataStore = dataStore,
                    dataModel = changeRequest.dataModel,
                    key = objectChange.key,
                    lastVersion = objectChange.lastVersion,
                    changes = objectChange.changes,
                    version = version,
                    updateSharedFlow = updateFlow
                )
            )
        }
    }

    storeAction.response.complete(
        ChangeResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

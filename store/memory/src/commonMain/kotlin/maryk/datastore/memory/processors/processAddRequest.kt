package maryk.datastore.memory.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.properties.IsRootModel
import maryk.core.properties.key
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias AddStoreAction<DM> = StoreAction<DM, AddRequest<DM>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootModel>

/** Processes an AddRequest in a [storeAction] into a data store from [dataStoreFetcher] */
internal suspend fun <DM : IsRootModel> processAddRequest(
    version: HLC,
    storeAction: StoreAction<DM, AddRequest<DM>, AddResponse<DM>>,
    dataStoreFetcher: IsStoreFetcher<*>,
    updateFlow: MutableSharedFlow<IsUpdateAction>
) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    @Suppress("UNCHECKED_CAST")
    val dataStore = (dataStoreFetcher as IsStoreFetcher<DM>).invoke(addRequest.dataModel)

    if (addRequest.objects.isNotEmpty()) {
        for (objectToAdd in addRequest.objects) {
            val key = addRequest.dataModel.key(objectToAdd)

            val status = processAdd(
                dataStore,
                addRequest.dataModel,
                key,
                version,
                objectToAdd,
                updateFlow
            )
            statuses += status
        }
    }

    storeAction.response.complete(
        AddResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

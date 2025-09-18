package maryk.datastore.indexeddb.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.datastore.indexeddb.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias AddStoreAction<DM> = StoreAction<DM, AddRequest<DM>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootDataModel>

/** Processes an AddRequest in a [storeAction] into a data store from [dataStoreFetcher] */
internal suspend fun <DM : IsRootDataModel> processAddRequest(
    version: HLC,
    storeAction: StoreAction<DM, AddRequest<DM>, AddResponse<DM>>,
    dataStoreFetcher: IsStoreFetcher<DM>,
    updateFlow: MutableSharedFlow<IsUpdateAction>
) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    val dataStore = dataStoreFetcher.invoke(addRequest.dataModel)

    if (addRequest.objects.isNotEmpty()) {
        for ((index, objectToAdd) in addRequest.objects.withIndex()) {
            val key = addRequest.keysForObjects?.getOrNull(index)
                ?: addRequest.dataModel.key(objectToAdd)

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

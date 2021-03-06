package maryk.datastore.memory.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.key
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update

internal typealias AddStoreAction<DM, P> = StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes an AddRequest in a [storeAction] into a data store from [dataStoreFetcher] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processAddRequest(
    version: HLC,
    storeAction: StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>,
    dataStoreFetcher: IsStoreFetcher<*, *>,
    updateSendChannel: SendChannel<Update<DM, P>>
) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(addRequest.dataModel) as DataStore<DM, P>

    if (addRequest.objects.isNotEmpty()) {
        for (objectToAdd in addRequest.objects) {
            val key = addRequest.dataModel.key(objectToAdd)

            val status = processAdd(
                dataStore,
                addRequest.dataModel,
                key,
                version,
                objectToAdd,
                updateSendChannel
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

package maryk.datastore.memory.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias ChangeStoreAction<DM, P> = StoreAction<DM, P, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ChangeRequest in a [storeAction] into a data store from [dataStoreFetcher] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processChangeRequest(
    version: HLC,
    storeAction: ChangeStoreAction<DM, P>,
    dataStoreFetcher: IsStoreFetcher<*, *>,
    updateFlow: MutableSharedFlow<IsUpdateAction>
) {
    val changeRequest = storeAction.request

    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(changeRequest.dataModel) as DataStore<DM, P>

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

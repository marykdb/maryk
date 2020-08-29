package maryk.datastore.memory.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

/**
 * Processes the initial changes to values into the data store
 */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processInitialChangesUpdate(
    storeAction: StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>,
    dataStoreFetcher: (IsRootValuesDataModel<*>) -> DataStore<*, *>,
    updateSendChannel: SendChannel<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel
    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(dataModel) as DataStore<DM, P>

    val update = storeAction.request.update as InitialChangesUpdate<DM, P>

    val changeStatuses = mutableListOf<IsChangeResponseStatus<DM>>()
    for (change in update.changes) {
        for (versionedChange in change.changes) {
            changeStatuses += processChange(
                dataStore,
                dataModel,
                change.key,
                null,
                versionedChange.changes,
                HLC(versionedChange.version),
                updateSendChannel
            )
        }
    }

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            ChangeResponse(
                dataModel,
                changeStatuses
            )
        )
    )
}

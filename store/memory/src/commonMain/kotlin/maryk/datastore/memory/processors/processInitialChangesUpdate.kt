package maryk.datastore.memory.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.statuses.IsAddOrChangeResponseStatus
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

/**
 * Processes the initial changes to values into the data store
 */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : IsValuesPropertyDefinitions> processInitialChangesUpdate(
    storeAction: StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>,
    dataStoreFetcher: (IsRootValuesDataModel<*>) -> DataStore<*, *>,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel
    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(dataModel) as DataStore<DM, P>

    val update = storeAction.request.update as InitialChangesUpdate<DM, P>

    val changeStatuses = mutableListOf<IsAddOrChangeResponseStatus<DM>>()
    for (change in update.changes) {
        for (versionedChange in change.changes) {
            if (versionedChange.changes.contains(ObjectCreate)) {
                val addedValues = dataModel.fromChanges(null, versionedChange.changes)

                changeStatuses += processAdd(
                    dataStore,
                    dataModel = dataModel,
                    key = change.key,
                    version = HLC(versionedChange.version),
                    objectToAdd = addedValues,
                    updateSharedFlow = updateSharedFlow
                )
            } else {
                changeStatuses += processChange(
                    dataStore,
                    dataModel,
                    change.key,
                    null,
                    versionedChange.changes,
                    HLC(versionedChange.version),
                    updateSharedFlow
                )
            }
        }
    }

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            AddOrChangeResponse(
                dataModel,
                changeStatuses
            )
        )
    )
}

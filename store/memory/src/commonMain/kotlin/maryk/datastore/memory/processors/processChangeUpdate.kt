package maryk.datastore.memory.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

/**
 * Processes the changes to values into the data store
 */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processChangeUpdate(
    storeAction: StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>,
    dataStoreFetcher: (IsRootValuesDataModel<*>) -> DataStore<*, *>,
    updateSendChannel: SendChannel<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel
    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(dataModel) as DataStore<DM, P>

    val update = storeAction.request.update as ChangeUpdate<DM, P>

    if (update.changes.contains(ObjectCreate)) {
        val addedValues = dataModel.fromChanges(null, update.changes)

        val response = processAdd(
            dataStore = dataStore,
            dataModel = dataModel,
            key = update.key,
            version = HLC(update.version),
            objectToAdd = addedValues,
            updateSendChannel = updateSendChannel
        )

        storeAction.response.complete(
            ProcessResponse(update.version, AddResponse(dataModel, listOf(response)))
        )
    } else {
        val response = processChange(
            dataStore,
            dataModel,
            update.key,
            null,
            update.changes,
            HLC(update.version),
            updateSendChannel
        )

        storeAction.response.complete(
            ProcessResponse(update.version, ChangeResponse(dataModel, listOf(response)))
        )
    }
}

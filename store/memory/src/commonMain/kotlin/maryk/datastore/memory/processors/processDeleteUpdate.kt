package maryk.datastore.memory.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

/**
 * Processes the deletion of values from the data store
 */
internal suspend fun <DM : IsRootDataModel<P>, P : IsValuesPropertyDefinitions> processDeleteUpdate(
    storeAction: StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>,
    dataStoreFetcher: (IsRootDataModel<*>) -> DataStore<*, *>,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel
    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(dataModel) as DataStore<DM, P>

    val update = storeAction.request.update as RemovalUpdate<DM, P>

    // Only delete from store
    val response = if (update.reason !== NotInRange) {
        // Delete it from history if it is a hard deletion
        val historicStoreIndexValuesWalker = if (update.reason == HardDelete && dataStore.keepAllVersions) {
            HistoricStoreIndexValuesWalker
        } else null

        processDelete(
            dataStore,
            dataModel,
            update.key,
            HLC(update.version),
            update.reason == HardDelete,
            historicStoreIndexValuesWalker,
            updateSharedFlow
        )
    } else {
        throw RequestException("NotInRange deletes are not allowed, don't do limits or filters on requests which need to be processed")
    }

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            DeleteResponse(
                dataModel,
                listOf(response)
            )
        )
    )
}

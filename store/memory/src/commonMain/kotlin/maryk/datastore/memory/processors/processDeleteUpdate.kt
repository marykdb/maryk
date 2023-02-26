package maryk.datastore.memory.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.properties.IsRootModel
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.query.responses.UpdateResponse
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

/**
 * Processes the deletion of values from the data store
 */
internal suspend fun <DM : IsRootModel> processDeleteUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    dataStoreFetcher: (IsRootModel) -> DataStore<*>,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel
    @Suppress("UNCHECKED_CAST")
    val dataStore = (dataStoreFetcher as IsStoreFetcher<DM>).invoke(dataModel)

    val update = storeAction.request.update as RemovalUpdate<DM>

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

package maryk.datastore.memory.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
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
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processDeleteUpdate(
    storeAction: StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse>,
    dataStoreFetcher: (IsRootValuesDataModel<*>) -> DataStore<*, *>,
    updateSendChannel: SendChannel<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel
    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(dataModel) as DataStore<DM, P>

    val update = storeAction.request.update as RemovalUpdate<DM, P>

    // Only delete from store
    if (update.reason !== NotInRange) {
        val index = dataStore.records.binarySearch { it.key.compareTo(update.key) }

        // Delete it from history if it is a hard deletion
        val historicStoreIndexValuesWalker = if (update.reason == HardDelete && dataStore.keepAllVersions) {
            HistoricStoreIndexValuesWalker
        } else null

        if (index >= 0) {
            processDelete(
                dataStore,
                dataModel,
                update.key,
                index,
                HLC(update.version),
                update.reason == HardDelete,
                historicStoreIndexValuesWalker,
                updateSendChannel
            )
        }
    }

    storeAction.response.complete(ProcessResponse())
}

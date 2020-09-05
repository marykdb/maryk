package maryk.datastore.memory.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias ProcessUpdateResponseStoreAction<DM, P> = StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>
internal typealias AnyProcessUpdateResponseStoreAction = ProcessUpdateResponseStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * Processes addition update within a store action.
 * The addition is stored in the data store if it does not exist yet.
 */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processAdditionUpdate(
    storeAction: StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>,
    dataStoreFetcher: (IsRootValuesDataModel<*>) -> DataStore<*, *>,
    updateSendChannel: SendChannel<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel
    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(dataModel) as DataStore<DM, P>

    val update = storeAction.request.update as AdditionUpdate<DM, P>

    if (update.firstVersion != update.version) {
        throw RequestException("Cannot process an AdditionUpdate with a version different than the first version. Use a query for changes to properly process changes into a data store")
    }

    val result = processAdd(
        dataStore = dataStore,
        dataModel = dataModel,
        key = update.key,
        version = HLC(update.version),
        objectToAdd = update.values,
        updateSendChannel = updateSendChannel
    )

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            AddResponse(dataModel, listOf(result))
        )
    )
}

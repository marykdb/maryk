package maryk.datastore.memory.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.properties.IsRootModel
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.UpdateResponse
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias ProcessUpdateResponseStoreAction<DM> = StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>
internal typealias AnyProcessUpdateResponseStoreAction = ProcessUpdateResponseStoreAction<IsRootModel>

/**
 * Processes addition update within a store action.
 * The addition is stored in the data store if it does not exist yet.
 */
internal suspend fun <DM : IsRootModel> processAdditionUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    dataStoreFetcher: (DM) -> DataStore<DM>,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel
    val dataStore = dataStoreFetcher(dataModel)

    val update = storeAction.request.update as AdditionUpdate<DM>

    if (update.firstVersion != update.version) {
        throw RequestException("Cannot process an AdditionUpdate with a version different than the first version. Use a query for changes to properly process changes into a data store")
    }

    val result = processAdd(
        dataStore = dataStore,
        dataModel = dataModel,
        key = update.key,
        version = HLC(update.version),
        objectToAdd = update.values,
        updateSharedFlow = updateSharedFlow
    )

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            AddResponse(dataModel, listOf(result))
        )
    )
}

package maryk.datastore.hbase.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias ProcessUpdateResponseStoreAction<DM> = StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>
internal typealias AnyProcessUpdateResponseStoreAction = ProcessUpdateResponseStoreAction<IsRootDataModel>

/** Processes an Addition Update in a [storeAction] into [dataStore] */
internal suspend fun <DM : IsRootDataModel> processAdditionUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    dataStore: HbaseDataStore,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel

    val update = storeAction.request.update as AdditionUpdate<DM>

    if (update.firstVersion != update.version) {
        throw RequestException("Cannot process an AdditionUpdate with a version different than the first version. Use a query for changes to properly process changes into a data store")
    }

    val dbIndex = dataStore.getDataModelId(dataModel)
    val table = dataStore.getTable(dataModel)

    val result = processAdd(
        dataStore,
        dataModel,
        dbIndex,
        table,
        update.key,
        HLC(update.version),
        update.values,
        updateSharedFlow
    )

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            AddResponse(dataModel, listOf(result))
        )
    )
}

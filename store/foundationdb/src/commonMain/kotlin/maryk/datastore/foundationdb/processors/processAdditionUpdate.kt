package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.shared.StoreAction

internal typealias ProcessUpdateResponseStoreAction<DM> = StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>
internal typealias AnyProcessUpdateResponseStoreAction = ProcessUpdateResponseStoreAction<IsRootDataModel>

/** Processes an Addition UpdateResponse into FoundationDB */
internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processAdditionUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
) {
    val dataModel = storeAction.request.dataModel
    val update = storeAction.request.update as AdditionUpdate<DM>

    val tableDirs = getTableDirs(getDataModelId(dataModel))

    val status = processAdd(
        tableDirs = tableDirs,
        dataModel = dataModel,
        key = update.key,
        version = HLC(update.version),
        objectToAdd = update.values,
    )

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            AddResponse(dataModel, listOf(status))
        )
    )
}


package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
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

    if (update.firstVersion != update.version) {
        throw RequestException("Cannot process an AdditionUpdate with a version different than the first version. Use a query for changes to properly process changes into a data store")
    }

    val tableDirs = getTableDirs(getDataModelId(dataModel))

    val status = processAdd(
        tableDirs = tableDirs,
        dataModel = dataModel,
        key = update.key,
        version = HLC(update.version),
        objectToAdd = update.values,
        isDeleted = update.isDeleted,
        ignoreIfVersionNotNewer = true,
    )

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            AddResponse(dataModel, listOf(status))
        )
    )
}

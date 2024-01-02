package maryk.datastore.hbase.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias DeleteStoreAction<DM> = StoreAction<DM, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootDataModel>

/** Processes a DeleteRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processDeleteRequest(
    version: HLC,
    storeAction: DeleteStoreAction<DM>,
    dataStore: HbaseDataStore,
    cache: Cache,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val deleteRequest = storeAction.request
    val statuses = mutableListOf<IsDeleteResponseStatus<DM>>()

    if (deleteRequest.keys.isNotEmpty()) {
        val dbIndex = dataStore.getDataModelId(deleteRequest.dataModel)

        for (key in deleteRequest.keys) {
            statuses += processDelete(
                dataStore,
                deleteRequest.dataModel,
                key,
                version,
                dbIndex,
                deleteRequest.hardDelete,
                cache,
                updateSharedFlow
            )
        }
    }

    storeAction.response.complete(
        DeleteResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

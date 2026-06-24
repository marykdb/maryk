package maryk.datastore.memory.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias DeleteStoreAction<DM> = StoreAction<DM, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootDataModel>

internal val objectSoftDeleteQualifier = byteArrayOf(0)

/** Processes a DeleteRequest in a [storeAction] into a data store from [dataStoreFetcher] */
internal suspend fun <DM : IsRootDataModel> processDeleteRequest(
    version: HLC,
    storeAction: DeleteStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<DM>,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val deleteRequest = storeAction.request
    val statuses = ArrayList<IsDeleteResponseStatus<DM>>(deleteRequest.keys.size.coerceAtLeast(4))

    if (deleteRequest.keys.isNotEmpty()) {
        val dataStore = dataStoreFetcher.invoke(deleteRequest.dataModel)

        for (key in deleteRequest.keys) {
            try {
                statuses.add(
                    processDelete(
                        dataStore,
                        deleteRequest.dataModel,
                        key,
                        version,
                        deleteRequest.hardDelete,
                        updateSharedFlow
                    )
                )
            } catch (e: Throwable) {
                e.rethrowIfFatal()
                statuses.add(ServerFail(e.toString(), e))
            }
        }
    }

    storeAction.response.complete(
        DeleteResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

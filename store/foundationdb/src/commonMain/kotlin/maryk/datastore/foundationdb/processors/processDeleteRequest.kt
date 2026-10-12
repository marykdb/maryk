package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.currentAtomicContext
import maryk.datastore.shared.StoreAction

internal typealias DeleteStoreAction<DM> = StoreAction<DM, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootDataModel>

/** Processes a DeleteRequest in a [storeAction] into a [FoundationDBDataStore] */

internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processDeleteRequest(
    version: HLC,
    storeAction: DeleteStoreAction<DM>,
    cache: Cache,
) {
    val deleteRequest = storeAction.request
    val statuses = ArrayList<IsDeleteResponseStatus<DM>>(deleteRequest.keys.size.coerceAtLeast(4))

    if (deleteRequest.keys.isNotEmpty()) {
        val dbIndex = getDataModelId(deleteRequest.dataModel)
        val tableDirs = getTableDirs(dbIndex)

        suspend fun deleteObjects() {
            for (key in deleteRequest.keys) {
                currentCoroutineContext().ensureActive()
                statuses += processDelete(
                    tableDirs = tableDirs,
                    dataModel = deleteRequest.dataModel,
                    key = key,
                    version = version,
                    dbIndex = dbIndex,
                    hardDelete = deleteRequest.hardDelete,
                    lastVersion = deleteRequest.lastVersion,
                    cache = cache,
                )
            }
        }
        if (currentAtomicContext(this) != null) deleteObjects()
        else withContext(Dispatchers.IO) { deleteObjects() }
    }

    storeAction.response.complete(
        DeleteResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

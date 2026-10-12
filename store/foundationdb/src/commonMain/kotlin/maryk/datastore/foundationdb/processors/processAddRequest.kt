package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.currentAtomicContext

internal typealias AddStoreAction<DM> = StoreAction<DM, AddRequest<DM>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootDataModel>

internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processAddRequest(
    version: HLC,
    storeAction: AddStoreAction<DM>
) {
    val addRequest = storeAction.request
    val statuses = ArrayList<IsAddResponseStatus<DM>>(addRequest.objects.size.coerceAtLeast(4))

    if (addRequest.objects.isNotEmpty()) {
        val dbIndex = getDataModelId(addRequest.dataModel)
        val tableDirs = getTableDirs(dbIndex)

        suspend fun addObjects() {
            for ((index, objectToAdd) in addRequest.objects.withIndex()) {
                currentCoroutineContext().ensureActive()
                val key = addRequest.keysForObjects?.getOrNull(index)
                    ?: addRequest.dataModel.key(objectToAdd)

                val status = processAdd(
                    tableDirs = tableDirs,
                    dataModel = addRequest.dataModel,
                    key = key,
                    version = version,
                    objectToAdd = objectToAdd,
                )

                statuses += status
            }
        }
        // Atomic execution already owns an IO worker and one transaction. Avoid
        // redispatching every member; ordinary adds retain their IO dispatch.
        if (currentAtomicContext(this) != null) addObjects()
        else withContext(Dispatchers.IO) { addObjects() }
    }

    storeAction.response.complete(
        AddResponse(
            addRequest.dataModel,
            statuses
        )
    )
}

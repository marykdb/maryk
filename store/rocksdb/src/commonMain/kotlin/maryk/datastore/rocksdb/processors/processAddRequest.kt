package maryk.datastore.rocksdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.withTransaction
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update

internal typealias AddStoreAction<DM> = StoreAction<DM, AddRequest<DM>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootDataModel>

/** Processes an AddRequest in a [storeAction] into a [RocksDBDataStore] */
internal suspend fun <DM : IsRootDataModel> RocksDBDataStore.processAddRequest(
    version: HLC,
    storeAction: StoreAction<DM, AddRequest<DM>, AddResponse<DM>>,
) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    if (addRequest.objects.isNotEmpty()) {
        val dbIndex = getDataModelId(addRequest.dataModel)
        val columnFamilies = getColumnFamilies(dbIndex)

        withTransaction { transaction ->
            for ((index, objectToAdd) in addRequest.objects.withIndex()) {
                val key = addRequest.keysForObjects?.getOrNull(index)
                    ?: addRequest.dataModel.key(objectToAdd)

                var updateToEmit: Update<DM>? = null

                statuses += processAdd(
                    addRequest.dataModel,
                    transaction,
                    columnFamilies,
                    dbIndex,
                    key,
                    version,
                    objectToAdd,
                ) { update ->
                    updateToEmit = update
                }

                transaction.commit()

                emitUpdate(updateToEmit)
            }
        }
    }

    storeAction.response.complete(
        AddResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

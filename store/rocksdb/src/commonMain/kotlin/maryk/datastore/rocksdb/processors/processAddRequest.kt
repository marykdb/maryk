package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias AddStoreAction<DM> = StoreAction<DM, AddRequest<DM>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootDataModel>

/** Processes an AddRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processAddRequest(
    version: HLC,
    storeAction: StoreAction<DM, AddRequest<DM>, AddResponse<DM>>,
    dataStore: RocksDBDataStore,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    if (addRequest.objects.isNotEmpty()) {
        val dbIndex = dataStore.getDataModelId(addRequest.dataModel)
        val columnFamilies = dataStore.getColumnFamilies(dbIndex)

        Transaction(dataStore).use { transaction ->
            for ((index, objectToAdd) in addRequest.objects.withIndex()) {
                val key = addRequest.keysForObjects?.getOrNull(index)
                    ?: addRequest.dataModel.key(objectToAdd)

                statuses += processAdd(
                    dataStore,
                    addRequest.dataModel,
                    transaction,
                    columnFamilies,
                    dbIndex,
                    key,
                    version,
                    objectToAdd,
                    updateSharedFlow
                )
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

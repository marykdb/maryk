package maryk.datastore.cassandra.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.datastore.cassandra.CassandraDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias AddStoreAction<DM> = StoreAction<DM, AddRequest<DM>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootDataModel>

/** Processes an AddRequest in a [storeAction] into a [dataStore] */
@Suppress("UNUSED_PARAMETER")
internal suspend fun <DM : IsRootDataModel> processAddRequest(
    version: HLC,
    storeAction: StoreAction<DM, AddRequest<DM>, AddResponse<DM>>,
    dataStore: CassandraDataStore,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    if (addRequest.objects.isNotEmpty()) {
        val dbIndex = dataStore.getDataModelId(addRequest.dataModel)



        TODO("Implement Cassandra add request" + dbIndex)
//        val columnFamilies = dataStore.getColumnFamilies(dbIndex)
//
//        Transaction(dataStore).use { transaction ->
//            for (objectToAdd in addRequest.objects) {
//                statuses += processAdd(
//                    dataStore,
//                    addRequest.dataModel,
//                    transaction,
//                    columnFamilies,
//                    dbIndex,
//                    addRequest.dataModel.key(objectToAdd),
//                    version,
//                    objectToAdd,
//                    updateSharedFlow
//                )
//            }
//        }
    }

    storeAction.response.complete(
        AddResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

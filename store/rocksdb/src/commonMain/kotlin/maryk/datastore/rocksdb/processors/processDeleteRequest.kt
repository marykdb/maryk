package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction

internal typealias DeleteStoreAction<DM, P> = StoreAction<DM, P, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootValuesDataModel<IsValuesPropertyDefinitions>, IsValuesPropertyDefinitions>

/** Processes a DeleteRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : IsValuesPropertyDefinitions> processDeleteRequest(
    version: HLC,
    storeAction: DeleteStoreAction<DM, P>,
    dataStore: RocksDBDataStore,
    cache: Cache,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val deleteRequest = storeAction.request
    val statuses = mutableListOf<IsDeleteResponseStatus<DM>>()

    if (deleteRequest.keys.isNotEmpty()) {
        val dbIndex = dataStore.getDataModelId(deleteRequest.dataModel)
        val columnFamilies = dataStore.getColumnFamilies(dbIndex)

        // Delete it from history if it is a hard deletion
        val historicStoreIndexValuesWalker = if (deleteRequest.hardDelete && columnFamilies is HistoricTableColumnFamilies) {
            HistoricStoreIndexValuesWalker(columnFamilies, dataStore.defaultReadOptions)
        } else null

        for (key in deleteRequest.keys) {
            statuses += processDelete(
                dataStore,
                deleteRequest.dataModel,
                columnFamilies,
                key,
                version,
                dbIndex,
                deleteRequest.hardDelete,
                historicStoreIndexValuesWalker,
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

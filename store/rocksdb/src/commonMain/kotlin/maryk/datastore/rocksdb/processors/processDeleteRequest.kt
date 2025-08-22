package maryk.datastore.rocksdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction

internal typealias DeleteStoreAction<DM> = StoreAction<DM, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootDataModel>

/** Processes a DeleteRequest in a [storeAction] into a [RocksDBDataStore] */
internal suspend fun <DM : IsRootDataModel> RocksDBDataStore.processDeleteRequest(
    version: HLC,
    storeAction: DeleteStoreAction<DM>,
    cache: Cache,
) {
    val deleteRequest = storeAction.request
    val statuses = mutableListOf<IsDeleteResponseStatus<DM>>()

    if (deleteRequest.keys.isNotEmpty()) {
        val dbIndex = getDataModelId(deleteRequest.dataModel)
        val columnFamilies = getColumnFamilies(dbIndex)

        // Delete it from history if it is a hard deletion
        val historicStoreIndexValuesWalker = if (deleteRequest.hardDelete && columnFamilies is HistoricTableColumnFamilies) {
            HistoricStoreIndexValuesWalker(columnFamilies, defaultReadOptions)
        } else null

        for (key in deleteRequest.keys) {
            statuses += processDelete(
                deleteRequest.dataModel,
                columnFamilies,
                key,
                version,
                dbIndex,
                deleteRequest.hardDelete,
                historicStoreIndexValuesWalker,
                cache,
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

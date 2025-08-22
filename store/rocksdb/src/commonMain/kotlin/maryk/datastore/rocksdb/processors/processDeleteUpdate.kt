package maryk.datastore.rocksdb.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction

/** Processes an update response with delete in a [storeAction] into [RocksDBDataStore] */
internal suspend fun <DM : IsRootDataModel> RocksDBDataStore.processDeleteUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    cache: Cache,
) {
    val dataModel = storeAction.request.dataModel

    val update = storeAction.request.update as RemovalUpdate<DM>

    // Only delete from store
    val response = if (update.reason !== NotInRange) {
        val dbIndex = getDataModelId(dataModel)
        val columnFamilies = getColumnFamilies(dbIndex)
        val hardDelete = update.reason == HardDelete

        // Delete it from history if it is a hard deletion
        val historicStoreIndexValuesWalker = if (hardDelete && columnFamilies is HistoricTableColumnFamilies) {
            HistoricStoreIndexValuesWalker(columnFamilies, defaultReadOptions)
        } else null

        processDelete(
            dataModel,
            columnFamilies,
            update.key,
            HLC(update.version),
            dbIndex,
            hardDelete,
            historicStoreIndexValuesWalker,
            cache,
        )
    } else {
        throw RequestException("NotInRange deletes are not allowed, don't do limits or filters on requests which need to be processed")
    }

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            DeleteResponse(
                dataModel,
                listOf(response)
            )
        )
    )
}

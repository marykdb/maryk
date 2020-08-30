package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update

/** Processes an update response with delete in a [storeAction] into [dataStore] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processDeleteUpdate(
    storeAction: StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>,
    dataStore: RocksDBDataStore,
    cache: Cache,
    updateSendChannel: SendChannel<Update<DM, P>>
) {
    val dataModel = storeAction.request.dataModel

    val update = storeAction.request.update as RemovalUpdate<DM, P>

    // Only delete from store
    val response = if (update.reason !== NotInRange) {
        val dbIndex = dataStore.getDataModelId(dataModel)
        val columnFamilies = dataStore.getColumnFamilies(dbIndex)
        val hardDelete = update.reason == HardDelete

        // Delete it from history if it is a hard deletion
        val historicStoreIndexValuesWalker = if (hardDelete && columnFamilies is HistoricTableColumnFamilies) {
            HistoricStoreIndexValuesWalker(columnFamilies, dataStore.defaultReadOptions)
        } else null

        processDelete(
            dataStore,
            dataModel,
            columnFamilies,
            update.key,
            HLC(update.version),
            dbIndex,
            hardDelete,
            historicStoreIndexValuesWalker,
            cache,
            updateSendChannel
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

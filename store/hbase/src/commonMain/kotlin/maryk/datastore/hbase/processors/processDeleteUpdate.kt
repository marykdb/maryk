package maryk.datastore.hbase.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.uniquesColumnFamily
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction
import org.apache.hadoop.hbase.client.Scan

/** Processes an update response with delete in a [storeAction] into [dataStore] */
internal suspend fun <DM : IsRootDataModel> processDeleteUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    dataStore: HbaseDataStore,
    cache: Cache,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel

    val update = storeAction.request.update as RemovalUpdate<DM>
    val table = dataStore.getTable(dataModel)

    // Only delete from store
    val response = if (update.reason !== NotInRange) {
        val dbIndex = dataStore.getDataModelId(dataModel)
        val hardDelete = update.reason == HardDelete

        // Fetch all row keys in column family
        val uniqueReferences = table.getScanner(Scan().apply {
            addFamily(uniquesColumnFamily)
            maxResultsPerColumnFamily = 1
        }).use { scanner ->
            scanner.iterator().asSequence().map { it.row }.toList()
        }

        processDelete(
            table,
            dataModel,
            uniqueReferences,
            update.key,
            HLC(update.version),
            dbIndex,
            hardDelete,
            cache,
            updateSharedFlow
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

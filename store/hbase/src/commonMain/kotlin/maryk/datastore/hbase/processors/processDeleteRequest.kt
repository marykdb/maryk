package maryk.datastore.hbase.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.uniquesColumnFamily
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction
import org.apache.hadoop.hbase.client.Scan

internal typealias DeleteStoreAction<DM> = StoreAction<DM, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootDataModel>

/** Processes a DeleteRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processDeleteRequest(
    version: HLC,
    storeAction: DeleteStoreAction<DM>,
    dataStore: HbaseDataStore,
    cache: Cache,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val deleteRequest = storeAction.request
    val statuses = mutableListOf<IsDeleteResponseStatus<DM>>()

    if (deleteRequest.keys.isNotEmpty()) {
        val dbIndex = dataStore.getDataModelId(deleteRequest.dataModel)
        val table = dataStore.getTable(deleteRequest.dataModel)

        // Fetch all row keys in column family
        val uniqueReferences = table.getScanner(Scan().apply {
            addFamily(uniquesColumnFamily)
            maxResultsPerColumnFamily = 1
        }).use { scanner ->
            scanner.iterator().asSequence().map { it.row }.toList()
        }

        for (key in deleteRequest.keys) {
            statuses += processDelete(
                table,
                deleteRequest.dataModel,
                uniqueReferences,
                key,
                version,
                dbIndex,
                deleteRequest.hardDelete,
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

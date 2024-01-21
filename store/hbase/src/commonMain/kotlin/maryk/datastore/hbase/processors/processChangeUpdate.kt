package maryk.datastore.hbase.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.future.await
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.RowMutations
import org.apache.hadoop.hbase.filter.Filter

/** Processes a UpdateResponse with Change in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processChangeUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    dataStore: HbaseDataStore,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel

    val changeRequest = storeAction.request

    val update = storeAction.request.update as ChangeUpdate<DM>

    val dbIndex = dataStore.getDataModelId(changeRequest.dataModel)
    val table = dataStore.getTable(dataModel)

    if (update.changes.contains(ObjectCreate)) {
        val addedValues = dataModel.fromChanges(null, update.changes)

        val status = processAdd(
            dataStore,
            dataModel,
            dbIndex,
            table,
            update.key,
            HLC(update.version),
            addedValues,
            updateSharedFlow
        )

        storeAction.response.complete(
            ProcessResponse(update.version, AddResponse(dataModel, listOf(status)))
        )
    } else {
        val status = try {
            val conditionalFilters = mutableListOf<Filter>()
            val rowMutations = RowMutations(update.key.bytes)
            val dependentPuts = mutableListOf<Put>()
            processChange(
                dataStore = dataStore,
                dataModel = changeRequest.dataModel,
                key = update.key,
                lastVersion = null,
                changes = update.changes,
                version = HLC(update.version),
                conditionalFilters = conditionalFilters,
                rowMutations = rowMutations,
                dependentPuts = dependentPuts,
                updateSharedFlow = updateSharedFlow
            ).also {
                table.mutateRow(rowMutations).await()
                table.putAll(dependentPuts).await()
            }
        } catch (e: Throwable) {
            ServerFail(e.message ?: e.toString(), e)
        }

        storeAction.response.complete(
            ProcessResponse(update.version, ChangeResponse(dataModel, listOf(status)))
        )
    }
}

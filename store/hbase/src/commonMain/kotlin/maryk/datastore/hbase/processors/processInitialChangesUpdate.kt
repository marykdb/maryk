package maryk.datastore.hbase.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.future.await
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.IsAddOrChangeResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.RowMutations
import org.apache.hadoop.hbase.filter.Filter

/** Processes a UpdateResponse with Change in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processInitialChangesUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    dataStore: HbaseDataStore,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val dataModel = storeAction.request.dataModel

    val changeRequest = storeAction.request

    val update = storeAction.request.update as InitialChangesUpdate<DM>

    val dbIndex = dataStore.getDataModelId(changeRequest.dataModel)
    val table = dataStore.getTable(dataModel)

    val changeStatuses = mutableListOf<IsAddOrChangeResponseStatus<DM>>()

    for (change in update.changes) {
        for (versionedChange in change.changes) {
            if (versionedChange.changes.contains(ObjectCreate)) {
                val addedValues = dataModel.fromChanges(null, versionedChange.changes)

                changeStatuses += try {
                    val response = processAdd(
                        dataStore,
                        dataModel,
                        dbIndex,
                        table,
                        change.key,
                        HLC(update.version),
                        addedValues,
                        updateSharedFlow
                    )

                    response
                } catch (e: Throwable) {
                    ServerFail(e.message ?: e.toString(), e)
                }
            } else {
                changeStatuses += try {
                    val conditionalFilters = mutableListOf<Filter>()
                    val rowMutations = RowMutations(change.key.bytes)
                    val dependentPuts = mutableListOf<Put>()
                    processChange(
                        dataStore = dataStore,
                        dataModel = changeRequest.dataModel,
                        key = change.key,
                        lastVersion = null,
                        changes = versionedChange.changes,
                        version = HLC(versionedChange.version),
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
            }
        }
    }

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            AddOrChangeResponse(
                dataModel,
                changeStatuses
            )
        )
    )
}

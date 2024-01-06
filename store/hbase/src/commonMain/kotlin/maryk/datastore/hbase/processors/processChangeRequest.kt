package maryk.datastore.hbase.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.future.await
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction
import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.RowMutations
import org.apache.hadoop.hbase.filter.Filter
import org.apache.hadoop.hbase.filter.FilterList

internal typealias ChangeStoreAction<DM> = StoreAction<DM, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootDataModel>

/** Processes a ChangeRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processChangeRequest(
    version: HLC,
    storeAction: ChangeStoreAction<DM>,
    dataStore: HbaseDataStore,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val changeRequest = storeAction.request

    val statuses = ArrayList<IsChangeResponseStatus<DM>>(changeRequest.objects.size)

    if (changeRequest.objects.isNotEmpty()) {
        val dbIndex = dataStore.getDataModelId(changeRequest.dataModel)

        val table = dataStore.getTable(changeRequest.dataModel)
        val allCheckAndMutations = ArrayList<Pair<List<Filter>, RowMutations>>(changeRequest.objects.size)

        for (objectChange in changeRequest.objects) {
            val conditionalFilters = mutableListOf<Filter>()
            val rowMutations = RowMutations(objectChange.key.bytes)
            allCheckAndMutations += conditionalFilters to rowMutations

            statuses += processChange(
                dataStore = dataStore,
                dataModel = changeRequest.dataModel,
                key = objectChange.key,
                lastVersion = objectChange.lastVersion,
                changes = objectChange.changes,
                dbIndex = dbIndex,
                version = version,
                conditionalFilters = conditionalFilters,
                rowMutations = rowMutations,
                updateSharedFlow = updateSharedFlow
            )
        }

        table.checkAndMutateAll(
            allCheckAndMutations.mapNotNull { (checks, mutations) ->
                if (mutations.mutations.isNotEmpty()) {
                    CheckAndMutate.newBuilder(mutations.row).apply {
                        ifMatches(FilterList(checks))
                    }.build(mutations)
                } else null
            }
        ).await()
    }

    storeAction.response.complete(
        ChangeResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

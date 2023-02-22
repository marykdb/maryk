package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.rocksdb.use

internal typealias ChangeStoreAction<DM, P> = StoreAction<DM, P, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootDataModel<IsValuesPropertyDefinitions>, IsValuesPropertyDefinitions>

/** Processes a ChangeRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel<P>, P : IsValuesPropertyDefinitions> processChangeRequest(
    version: HLC,
    storeAction: ChangeStoreAction<DM, P>,
    dataStore: RocksDBDataStore,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) {
    val changeRequest = storeAction.request

    val statuses = mutableListOf<IsChangeResponseStatus<DM>>()

    if (changeRequest.objects.isNotEmpty()) {
        val dbIndex = dataStore.getDataModelId(changeRequest.dataModel)
        val columnFamilies = dataStore.getColumnFamilies(dbIndex)

        Transaction(dataStore).use { transaction ->
            for (objectChange in changeRequest.objects) {
                statuses += processChange(
                    dataStore,
                    changeRequest.dataModel,
                    columnFamilies,
                    objectChange.key,
                    objectChange.lastVersion,
                    objectChange.changes,
                    transaction,
                    dbIndex,
                    version,
                    updateSharedFlow
                )
            }
            transaction.commit()
        }
    }

    storeAction.response.complete(
        ChangeResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

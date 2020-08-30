package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update
import maryk.rocksdb.use

internal typealias ChangeStoreAction<DM, P> = StoreAction<DM, P, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ChangeRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processChangeRequest(
    version: HLC,
    storeAction: ChangeStoreAction<DM, P>,
    dataStore: RocksDBDataStore,
    updateSendChannel: SendChannel<Update<DM, P>>
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
                    updateSendChannel
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

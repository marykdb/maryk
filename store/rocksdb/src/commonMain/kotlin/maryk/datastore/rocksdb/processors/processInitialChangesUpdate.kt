package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update
import maryk.rocksdb.use

/** Processes a UpdateResponse with Change in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processInitialChangesUpdate(
    storeAction: StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>,
    dataStore: RocksDBDataStore,
    updateSendChannel: SendChannel<Update<DM, P>>
) {
    val dataModel = storeAction.request.dataModel

    val changeRequest = storeAction.request

    val update = storeAction.request.update as InitialChangesUpdate<DM, P>

    val dbIndex = dataStore.getDataModelId(changeRequest.dataModel)
    val columnFamilies = dataStore.getColumnFamilies(dbIndex)

    val changeStatuses = mutableListOf<IsChangeResponseStatus<DM>>()

    Transaction(dataStore).use { transaction ->
        for (change in update.changes) {
            for (versionedChange in change.changes) {
                changeStatuses += try {
                    val response = processChange(
                        dataStore,
                        dataModel,
                        columnFamilies,
                        change.key,
                        null,
                        versionedChange.changes,
                        transaction,
                        dbIndex,
                        HLC(versionedChange.version),
                        updateSendChannel
                    )
                    transaction.commit()

                    response
                } catch (e: Throwable) {
                    ServerFail<DM>(e.message ?: e.toString(), e)
                }
            }
        }
    }

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            ChangeResponse(
                dataModel,
                changeStatuses
            )
        )
    )
}

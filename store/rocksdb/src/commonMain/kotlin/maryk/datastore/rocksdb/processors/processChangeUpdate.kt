package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update
import maryk.rocksdb.use

/** Processes a UpdateResponse with Change in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processChangeUpdate(
    storeAction: StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>,
    dataStore: RocksDBDataStore,
    updateSendChannel: SendChannel<Update<DM, P>>
) {
    val dataModel = storeAction.request.dataModel

    val changeRequest = storeAction.request

    val update = storeAction.request.update as ChangeUpdate<DM, P>

    val dbIndex = dataStore.getDataModelId(changeRequest.dataModel)
    val columnFamilies = dataStore.getColumnFamilies(dbIndex)

    if (update.changes.contains(ObjectCreate)) {
        val addedValues = dataModel.fromChanges(null, update.changes)

        val status = Transaction(dataStore).use { transaction ->
            processAdd(
                dataStore = dataStore,
                dataModel = dataModel,
                transaction = transaction,
                columnFamilies = columnFamilies,
                dbIndex = dbIndex,
                key = update.key,
                version = HLC(update.version),
                objectToAdd = addedValues,
                updateSendChannel = updateSendChannel
            )
        }

        storeAction.response.complete(
            ProcessResponse(update.version, AddResponse(dataModel, listOf(status)))
        )
    } else {
        val status = try {
            Transaction(dataStore).use { transaction ->
                val response = processChange(
                    dataStore,
                    dataModel,
                    columnFamilies,
                    update.key,
                    null,
                    update.changes,
                    transaction,
                    dbIndex,
                    HLC(update.version),
                    updateSendChannel
                )
                transaction.commit()

                response
            }
        } catch (e: Throwable) {
            ServerFail<DM>(e.message ?: e.toString(), e)
        }

        storeAction.response.complete(
            ProcessResponse(update.version, ChangeResponse(dataModel, listOf(status)))
        )
    }
}

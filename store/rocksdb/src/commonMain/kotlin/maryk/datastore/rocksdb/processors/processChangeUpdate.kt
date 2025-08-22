package maryk.datastore.rocksdb.processors

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
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.StoreAction

/** Processes a UpdateResponse with Change in a [storeAction] into a [RocksDBDataStore] */
internal suspend fun <DM : IsRootDataModel> RocksDBDataStore.processChangeUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
) {
    val dataModel = storeAction.request.dataModel

    val changeRequest = storeAction.request

    val update = storeAction.request.update as ChangeUpdate<DM>

    val dbIndex = getDataModelId(changeRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)

    if (update.changes.contains(ObjectCreate)) {
        val addedValues = dataModel.fromChanges(null, update.changes)

        val status = Transaction(this).use { transaction ->
            processAdd(
                dataModel = dataModel,
                transaction = transaction,
                columnFamilies = columnFamilies,
                dbIndex = dbIndex,
                key = update.key,
                version = HLC(update.version),
                objectToAdd = addedValues,
            )
        }

        storeAction.response.complete(
            ProcessResponse(update.version, AddResponse(dataModel, listOf(status)))
        )
    } else {
        val status = try {
            Transaction(this).use { transaction ->
                val response = processChange(
                    dataModel,
                    columnFamilies,
                    update.key,
                    null,
                    update.changes,
                    transaction,
                    dbIndex,
                    HLC(update.version),
                )
                transaction.commit()

                response
            }
        } catch (e: Throwable) {
            ServerFail(e.message ?: e.toString(), e)
        }

        storeAction.response.complete(
            ProcessResponse(update.version, ChangeResponse(dataModel, listOf(status)))
        )
    }
}

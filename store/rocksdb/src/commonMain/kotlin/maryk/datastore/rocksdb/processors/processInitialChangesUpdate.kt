package maryk.datastore.rocksdb.processors

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
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.StoreAction

/** Processes a UpdateResponse with Change in a [storeAction] into a [RocksDBDataStore] */
internal suspend fun <DM : IsRootDataModel> RocksDBDataStore.processInitialChangesUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
) {
    val dataModel = storeAction.request.dataModel

    val changeRequest = storeAction.request

    val update = storeAction.request.update as InitialChangesUpdate<DM>

    val dbIndex = getDataModelId(changeRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)

    val changeStatuses = mutableListOf<IsAddOrChangeResponseStatus<DM>>()

    Transaction(this).use { transaction ->
        for (change in update.changes) {
            for (versionedChange in change.changes) {
                if (versionedChange.changes.contains(ObjectCreate)) {
                    val addedValues = dataModel.fromChanges(null, versionedChange.changes)

                    changeStatuses += try {
                        val response = processAdd(
                            dataModel = dataModel,
                            transaction = transaction,
                            columnFamilies = columnFamilies,
                            dbIndex = dbIndex,
                            key = change.key,
                            version = HLC(versionedChange.version),
                            objectToAdd = addedValues,
                        )
                        transaction.commit()

                        response
                    } catch (e: Throwable) {
                        ServerFail(e.message ?: e.toString(), e)
                    }
                } else {
                    changeStatuses += try {
                        val response = processChange(
                            dataModel,
                            columnFamilies,
                            change.key,
                            null,
                            versionedChange.changes,
                            transaction,
                            dbIndex,
                            HLC(versionedChange.version),
                        )
                        transaction.commit()

                        response
                    } catch (e: Throwable) {
                        ServerFail(e.message ?: e.toString(), e)
                    }
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

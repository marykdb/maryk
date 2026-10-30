package maryk.datastore.rocksdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.IsAddOrChangeResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.withTransaction
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.fromReplicationChanges
import maryk.datastore.shared.normalizeReplicationChanges
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.updates.Update

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

    withTransaction { transaction ->
        for (change in update.changes) {
            for (versionedChange in change.changes) {
                val changes = normalizeReplicationChanges(versionedChange.changes)
                if (changes.contains(ObjectCreate)) {
                    val addedValues = dataModel.fromReplicationChanges(null, changes)

                    changeStatuses += try {
                        var updateToEmit: Update<DM>? = null
                        val response = processAdd(
                            dataModel = dataModel,
                            transaction = transaction,
                            columnFamilies = columnFamilies,
                            dbIndex = dbIndex,
                            key = change.key,
                            version = HLC(versionedChange.version),
                            objectToAdd = addedValues,
                            isDeleted = changes.filterIsInstance<ObjectSoftDeleteChange>().lastOrNull()?.isDeleted ?: false,
                            ignoreIfVersionNotNewer = true,
                        ) { updateToEmit = it }
                        transaction.commit()

                        emitUpdate(updateToEmit)

                        response
                    } catch (e: Throwable) {
                        e.rethrowIfFatal()
                        ServerFail(e.message ?: e.toString(), e)
                    }
                } else {
                    changeStatuses += try {
                        var updateToEmit: Update<DM>? = null
                        val response = processChange(
                            dataModel,
                            columnFamilies,
                            change.key,
                            null,
                            changes,
                            transaction,
                            dbIndex,
                            HLC(versionedChange.version),
                            ignoreIfVersionNotNewer = true,
                        ){
                            updateToEmit = it
                        }
                        transaction.commit()

                        emitUpdate(updateToEmit)

                        response
                    } catch (e: Throwable) {
                        e.rethrowIfFatal()
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

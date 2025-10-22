package maryk.datastore.rocksdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.withTransaction
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update

internal typealias ChangeStoreAction<DM> = StoreAction<DM, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootDataModel>

/** Processes a ChangeRequest in a [storeAction] into a [RocksDBDataStore] */
internal suspend fun <DM : IsRootDataModel> RocksDBDataStore.processChangeRequest(
    version: HLC,
    storeAction: ChangeStoreAction<DM>,
) {
    val changeRequest = storeAction.request

    val statuses = mutableListOf<IsChangeResponseStatus<DM>>()

    if (changeRequest.objects.isNotEmpty()) {
        val dbIndex = getDataModelId(changeRequest.dataModel)
        val columnFamilies = getColumnFamilies(dbIndex)

        withTransaction { transaction ->
            val updatesToEmit = mutableListOf<Update<DM>>()

            for (objectChange in changeRequest.objects) {
                statuses += processChange(
                    changeRequest.dataModel,
                    columnFamilies,
                    objectChange.key,
                    objectChange.lastVersion,
                    objectChange.changes,
                    transaction,
                    dbIndex,
                    version,
                ) {
                    updatesToEmit += it
                }
            }
            transaction.commit()

            emitUpdates(updatesToEmit)
        }
    }

    storeAction.response.complete(
        ChangeResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

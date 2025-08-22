package maryk.datastore.rocksdb.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.StoreAction

internal typealias ProcessUpdateResponseStoreAction<DM> = StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>
internal typealias AnyProcessUpdateResponseStoreAction = ProcessUpdateResponseStoreAction<IsRootDataModel>

/** Processes an Addition Update in a [storeAction] into [RocksDBDataStore] */
internal suspend fun <DM : IsRootDataModel> RocksDBDataStore.processAdditionUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
) {
    val dataModel = storeAction.request.dataModel

    val update = storeAction.request.update as AdditionUpdate<DM>

    if (update.firstVersion != update.version) {
        throw RequestException("Cannot process an AdditionUpdate with a version different than the first version. Use a query for changes to properly process changes into a data store")
    }

    val dbIndex = getDataModelId(dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)

    val result = Transaction(this).use { transaction ->
        processAdd(
            dataModel,
            transaction,
            columnFamilies,
            dbIndex,
            update.key,
            HLC(update.version),
            update.values,
        )
    }

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            AddResponse(dataModel, listOf(result))
        )
    )
}

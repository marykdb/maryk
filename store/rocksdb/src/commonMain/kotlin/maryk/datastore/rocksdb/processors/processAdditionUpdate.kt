package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.key
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update

internal typealias ProcessUpdateResponseStoreAction<DM, P> = StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>
internal typealias AnyProcessUpdateResponseStoreAction = ProcessUpdateResponseStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes an Addition Update in a [storeAction] into [dataStore] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processAdditionUpdate(
    storeAction: StoreAction<DM, P, UpdateResponse<DM, P>, ProcessResponse<DM>>,
    dataStore: RocksDBDataStore,
    updateSendChannel: SendChannel<Update<DM, P>>
) {
    val dataModel = storeAction.request.dataModel

    val update = storeAction.request.update as AdditionUpdate<DM, P>

    if (update.firstVersion != update.version) {
        throw RequestException("Cannot process an AdditionUpdate with a version different than the first version. Use a query for changes to properly process changes into a data store")
    }

    val dbIndex = dataStore.getDataModelId(dataModel)
    val columnFamilies = dataStore.getColumnFamilies(dbIndex)

    val result = processAdd(
        dataModel,
        dataStore,
        columnFamilies,
        dbIndex,
        dataModel.key(update.values),
        HLC(update.version),
        update.values,
        updateSendChannel
    )

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            AddResponse(dataModel, listOf(result))
        )
    )
}

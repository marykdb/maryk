package maryk.datastore.memory

import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update

internal typealias StoreExecutor<DM, P> = suspend Unit.(
    StoreAction<DM, P, *, *>,
    dataStoreFetcher: (IsRootValuesDataModel<P>) -> DataStore<DM, P>,
    updateSendChannel: SendChannel<Update<DM, P>>
) -> Unit

/**
 * DataProcessor that stores all data changes in local memory.
 * Very useful for tests.
 */
class InMemoryDataStore(
    override val keepAllVersions: Boolean = false,
    dataModelsById: Map<UInt, RootDataModel<*, *>>
) : AbstractDataStore(dataModelsById) {
    override val dataModelIdsByString = dataModelsById.map { (index, dataModel) ->
        Pair(dataModel.name, index)
    }.toMap()

    override val storeActor = this.storeActor(this, storeExecutor)

    override fun close() {
        super.close()
        storeActor.close()
    }
}

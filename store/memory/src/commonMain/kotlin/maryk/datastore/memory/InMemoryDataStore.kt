package maryk.datastore.memory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.StoreActor

internal typealias StoreExecutor<DM, P> = Unit.(StoreAction<DM, P, *, *>, dataStore: DataStore<DM, P>) -> Unit

internal expect fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> CoroutineScope.storeActor(
    store: InMemoryDataStore,
    executor: StoreExecutor<DM, P>
): StoreActor<DM, P>

/**
 * DataProcessor that stores all data changes in local memory.
 * Very useful for tests.
 */
class InMemoryDataStore(
    override val keepAllVersions: Boolean = false,
    dataModelsById: Map<UInt, RootDataModel<*, *>>
) : AbstractDataStore(dataModelsById) {
    override val coroutineContext = Dispatchers.Default

    override val dataModelIdsByString = dataModelsById.map { (index, dataModel) ->
        Pair(dataModel.name, index)
    }.toMap()

    private val dataActors: MutableMap<String, StoreActor<*, *>> = mutableMapOf()

    override fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> getStoreActor(dataModel: DM) =
        dataActors.getOrPut(dataModel.name) {
            if (!this.dataModelIdsByString.containsKey(dataModel.name)) {
                throw DefNotFoundException("DataModel ${dataModel.name} was not defined on this InMemoryStore")
            }
            @Suppress("UNCHECKED_CAST")
            this.storeActor(this, storeExecutor as StoreExecutor<DM, P>) as StoreActor<*, *>
        }

    override fun close() {
        // Nothing to close
    }
}

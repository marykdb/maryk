package maryk.datastore.memory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.StoreActor
import maryk.datastore.memory.records.DataStore

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
    val keepAllVersions: Boolean = false
) : AbstractDataStore() {
    override val coroutineContext = Dispatchers.Default
    private val dataActors: MutableMap<String, StoreActor<*, *>> = mutableMapOf()

    override fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> getStoreActor(dataModel: DM) =
        dataActors.getOrPut(dataModel.name) {
            @Suppress("UNCHECKED_CAST")
            this.storeActor(this, storeExecutor as StoreExecutor<DM, P>) as StoreActor<*, *>
        }
}

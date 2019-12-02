package maryk.datastore.memory

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.StoreActor

internal typealias StoreExecutor<DM, P> = Unit.(StoreAction<DM, P, *, *>, dataStore: DataStore<DM, P>) -> Unit

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
        super.close()
        dataActors.entries.forEach { (_, value) ->
            value.close()
        }
    }
}

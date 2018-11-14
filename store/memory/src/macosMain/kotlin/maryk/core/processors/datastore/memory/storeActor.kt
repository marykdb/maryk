package maryk.core.processors.datastore.memory

import kotlinx.coroutines.CoroutineScope
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions

internal actual fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> CoroutineScope.storeActor(store: InMemoryDataStore, executor: StoreExecutor<DM, P>): StoreActor<DM, P> {
    TODO()
}

@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.actor
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.records.DataRecord
import java.util.LinkedList

internal actual fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> CoroutineScope.storeActor(store: InMemoryDataStore, executor: StoreExecutor<DM, P>): StoreActor<DM, P> = actor {
    val listOfData = LinkedList<DataRecord<DM, P>>()

    for (msg in channel) { // iterate over incoming messages
        try {
            executor(Unit, store, msg, listOfData)
        } catch (e: Throwable) {
            msg.response.completeExceptionally(e)
        }
    }
}

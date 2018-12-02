package maryk.datastore.memory

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.IsDataStore
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.datastore.memory.records.DataStore
import kotlin.coroutines.CoroutineContext

internal typealias StoreExecutor<DM, P> = Unit.(StoreAction<DM, P, *, *>, dataStore: DataStore<DM, P>) -> Unit
internal typealias StoreActor<DM, P> = SendChannel<StoreAction<DM, P, *, *>>

internal expect fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> CoroutineScope.storeActor(store: InMemoryDataStore, executor: StoreExecutor<DM, P>): StoreActor<DM, P>

/**
 * DataProcessor that stores all data changes in local memory.
 * Very useful for tests.
 */
@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")
class InMemoryDataStore(
    val keepAllVersions: Boolean = false
) : IsDataStore, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = GlobalScope.coroutineContext

    private val dataActors: MutableMap<String, StoreActor<*, *>> = mutableMapOf()

    private fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> getStoreActor(scope: CoroutineScope, dataModel: DM) =
        dataActors.getOrPut(dataModel.name) {
            @Suppress("UNCHECKED_CAST")
            scope.storeActor(this, storeExecutor as StoreExecutor<DM, P>) as StoreActor<*, *>
        }

    override suspend fun <DM : RootDataModel<DM, P>, P : PropertyDefinitions, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP {
        val storeActor = this.getStoreActor(this, request.dataModel)
        val response = CompletableDeferred<RP>()

        storeActor.send(
            StoreAction(request, response)
        )

        return response.await()
    }
}

package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.SendChannel
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse

typealias StoreActor = SendChannel<StoreAction<*, *, *, *>>

/**
 * Abstract DataStore implementation that takes care of the HLC clock
 */
abstract class AbstractDataStore(
    final override val dataModelsById: Map<UInt, RootDataModel<*, *>>
): IsDataStore, CoroutineScope {
    private val dataStoreJob = SupervisorJob()
    override val coroutineContext = Dispatchers.Default + dataStoreJob

    /** StoreActor to run actions against.*/
    abstract val storeActor: StoreActor

    override val dataModelIdsByString = dataModelsById.map { (index, dataModel) ->
        Pair(dataModel.name, index)
    }.toMap()

    // Clock actor holds/calculates the latest HLC clock instance
    private val clockActor = this.clockActor()

    override suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP {
        val response = CompletableDeferred<RP>()

        val clock = DeferredClock().also {
            clockActor.send(it)
        }.completableDeferred.await()

        storeActor.send(
            StoreAction(clock, request, response)
        )

        return response.await()
    }

    /** Get [dataModel] id to identify it for storage */
    fun getDataModelId(dataModel: IsRootValuesDataModel<*>) =
        dataModelIdsByString[dataModel.name] ?:
        throw DefNotFoundException("DataStore not found ${dataModel.name}")

    override fun close() {
        dataStoreJob.cancel()
        clockActor.close()
    }
}

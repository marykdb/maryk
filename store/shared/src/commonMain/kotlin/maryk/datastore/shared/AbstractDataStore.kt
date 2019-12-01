package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse

typealias StoreActor<DM, P> = SendChannel<StoreAction<DM, P, *, *>>

/**
 * Abstract DataStore implementation that takes care of the HLC clock
 */
abstract class AbstractDataStore(
    final override val dataModelsById: Map<UInt, RootDataModel<*, *>>
): IsDataStore, CoroutineScope {
    override val dataModelIdsByString = dataModelsById.map { (index, dataModel) ->
        Pair(dataModel.name, index)
    }.toMap()

    // Clock actor holds/calculates the latest HLC clock instance
    private val clockActor by lazy { this.clockActor() }

    /** Get a StoreActor for [dataModel] to run actions against.*/
    abstract fun <DM: IsRootValuesDataModel<P>, P : PropertyDefinitions> getStoreActor(dataModel: DM): StoreActor<DM, P>

    override suspend fun <DM : RootDataModel<DM, P>, P : PropertyDefinitions, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP {
        val storeActor = this.getStoreActor(request.dataModel)
        val response = CompletableDeferred<RP>()

        val clock = DeferredClock().also {
            clockActor.send(it)
        }.completableDeferred.await()

        val dbIndex = dataModelIdsByString[request.dataModel.name] ?:
            throw DefNotFoundException("DataStore not found ${request.dataModel.name}")

        storeActor.send(
            StoreAction(clock, dbIndex, request, response)
        )

        return response.await()
    }

    override fun close() {
        clockActor.close()
    }
}

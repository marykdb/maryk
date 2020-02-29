package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.UpdateListener
import maryk.datastore.shared.updates.processUpdateActor

typealias StoreActor = SendChannel<StoreAction<*, *, *, *>>

/**
 * Abstract DataStore implementation that takes care of the HLC clock
 */
abstract class AbstractDataStore(
    final override val dataModelsById: Map<UInt, RootDataModel<*, *>>
): IsDataStore, CoroutineScope {
    override val coroutineContext = DISPATCHER + SupervisorJob()

    val updateListeners = mutableMapOf<UInt, MutableList<UpdateListener<*, *>>>()
    val updateSendChannel = processUpdateActor()

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

    @FlowPreview
    @ExperimentalCoroutinesApi
    override fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ, RP : IsResponse> executeFlow(
        request: RQ
    ): Flow<Update<DM>> where RQ : IsStoreRequest<DM, RP>, RQ: IsChangesRequest<DM, P, RP> {
        val channel = BroadcastChannel<Update<DM>>(Channel.BUFFERED)

        val dataModelId = getDataModelId(request.dataModel)

        this.updateListeners.getOrPut(dataModelId) { mutableListOf() } += UpdateListener(
            request,
            channel
        )

        return channel.asFlow()
    }

    /** Get [dataModel] id to identify it for storage */
    fun getDataModelId(dataModel: IsRootValuesDataModel<*>) =
        dataModelIdsByString[dataModel.name] ?:
        throw DefNotFoundException("DataStore not found ${dataModel.name}")

    override fun close() {
        this.cancel()

        clockActor.close()

        updateSendChannel.close()
        updateListeners.values.forEach { it.forEach(UpdateListener<*, *>::close) }
        updateListeners.clear()
    }
}

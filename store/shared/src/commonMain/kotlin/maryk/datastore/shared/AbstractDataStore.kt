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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.IsUpdatesRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.datastore.shared.updates.UpdateListener
import maryk.datastore.shared.updates.UpdateListenerForGet
import maryk.datastore.shared.updates.UpdateListenerForScan
import maryk.datastore.shared.updates.processUpdateActor

typealias StoreActor = SendChannel<StoreAction<*, *, *, *>>

/**
 * Abstract DataStore implementation that takes care of the HLC clock
 */
abstract class AbstractDataStore(
    final override val dataModelsById: Map<UInt, RootDataModel<*, *>>
): IsDataStore, CoroutineScope {
    override val coroutineContext = DISPATCHER + SupervisorJob()

    val updateListeners = mutableMapOf<UInt, MutableList<UpdateListener<*, *, *>>>()
    val updateSendChannel = processUpdateActor<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>()

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
    override suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ> executeFlow(
        request: RQ
    ): Flow<IsUpdateResponse<DM, P>>
        where RQ : IsStoreRequest<DM, UpdatesResponse<DM, P>>, RQ: IsUpdatesRequest<DM, P, UpdatesResponse<DM, P>> {
        if (request.toVersion != null) {
            throw RequestException("Cannot use toVersion on an executeFlow request")
        }

        // Don't allow filters with mutable or reference values
        request.where?.singleReference {
            !it.propertyDefinition.required || !it.propertyDefinition.final || it.comparablePropertyDefinition is IsReferenceDefinition<*, *, *>
        }?.let {
            throw RequestException("$it is mutable or a reference which are not supported on filters in update listeners.")
        }

        val channel = BroadcastChannel<IsUpdateResponse<DM, P>>(Channel.BUFFERED)

        val dataModelId = getDataModelId(request.dataModel)

        val dataModelUpdateListeners = this.updateListeners.getOrPut(dataModelId) { mutableListOf() }

        val response = execute(request)

        val listener = request.createUpdateListener(response, channel)

        dataModelUpdateListeners += listener

        return channel.asFlow().onStart {
            for (update in response.updates) {
                emit(update)
            }
        }.onCompletion {
            listener.close()
            dataModelUpdateListeners -= listener
        }
    }

    /** Get [dataModel] id to identify it for storage */
    fun getDataModelId(dataModel: IsRootValuesDataModel<*>) =
        dataModelIdsByString[dataModel.name] ?:
        throw DefNotFoundException("DataStore not found ${dataModel.name}")

    override fun close() {
        this.cancel()

        clockActor.close()

        updateSendChannel.close()

        closeAllListeners()
    }

    override fun closeAllListeners() {
        updateListeners.values.forEach { it.forEach(UpdateListener<*, *, *>::close) }
        updateListeners.clear()
    }
}

/** Creates update listener for request on [channel] */
private fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> IsChangesRequest<DM, P, *>.createUpdateListener(
    updatesResponse: UpdatesResponse<DM, P>,
    channel: SendChannel<IsUpdateResponse<DM, P>>
) =
    when (this) {
        is ScanUpdatesRequest<DM, P> -> {
            UpdateListenerForScan(
                request = this,
                scanRange = this.dataModel.createScanRange(this.where, this.startKey?.bytes, this.includeStart),
                scanResponse = updatesResponse,
                sendChannel = channel
            )
        }
        is GetUpdatesRequest<DM, P> -> {
            UpdateListenerForGet(
                this,
                updatesResponse,
                channel
            )
        }
        else -> throw RequestException("Unsupported request type for update listener: $this")
    }

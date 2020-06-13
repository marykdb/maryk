package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.IsUpdatesRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.datastore.shared.updates.AddUpdateListenerAction
import maryk.datastore.shared.updates.RemoveAllUpdateListenersAction
import maryk.datastore.shared.updates.RemoveUpdateListenerAction
import maryk.datastore.shared.updates.UpdateListenerForGet
import maryk.datastore.shared.updates.UpdateListenerForScan
import maryk.datastore.shared.updates.processUpdateActor
import maryk.lib.concurrency.AtomicReference

typealias StoreActor = SendChannel<StoreAction<*, *, *, *>>

/**
 * Abstract DataStore implementation that takes care of the HLC clock
 */
abstract class AbstractDataStore(
    final override val dataModelsById: Map<UInt, RootDataModel<*, *>>
): IsDataStore, CoroutineScope {
    override val coroutineContext = DISPATCHER + SupervisorJob()

    private val initIsDone: AtomicReference<Boolean> = AtomicReference(false)

    private val updateSendChannelHasStarted = CompletableDeferred<Unit>()
    val updateSendChannel = processUpdateActor<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(updateSendChannelHasStarted)

    protected val storeActorHasStarted = CompletableDeferred<Unit>()
    /** StoreActor to run actions against.*/
    abstract val storeActor: StoreActor

    private val clockActorHasStarted = CompletableDeferred<Unit>()
    // Clock actor holds/calculates the latest HLC clock instance
    private val clockActor = this.clockActor(clockActorHasStarted)

    override val dataModelIdsByString = dataModelsById.map { (index, dataModel) ->
        Pair(dataModel.name, index)
    }.toMap()

    suspend fun waitForInit() {
        if (!initIsDone.get()) {
            clockActorHasStarted.await()
            storeActorHasStarted.await()
            updateSendChannelHasStarted.await()
            initIsDone.set(true)
        }
    }

    override suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP {
        waitForInit()

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

        waitForInit()

        val dataModelId = getDataModelId(request.dataModel)

        val response = execute(request)

        val listener = request.createUpdateListener(response)

        updateSendChannel.send(AddUpdateListenerAction(dataModelId, listener))

        return listener.getFlow().onCompletion {
            updateSendChannel.send(RemoveUpdateListenerAction(dataModelId, listener))
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
    }

    override suspend fun closeAllListeners() {
        updateSendChannel.send(RemoveAllUpdateListenersAction)
    }
}

/** Creates update listener for request with [updatesResponse] */
private fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> IsUpdatesRequest<DM, P, UpdatesResponse<DM, P>>.createUpdateListener(
    updatesResponse: UpdatesResponse<DM, P>
) =
    when (this) {
        is ScanUpdatesRequest<DM, P> -> {
            UpdateListenerForScan(
                request = this,
                scanRange = this.dataModel.createScanRange(this.where, this.startKey?.bytes, this.includeStart),
                updatesResponse = updatesResponse
            )
        }
        is GetUpdatesRequest<DM, P> -> {
            UpdateListenerForGet(
                this,
                updatesResponse
            )
        }
        else -> throw RequestException("Unsupported request type for update listener: $this")
    }

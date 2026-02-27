package maryk.datastore.shared

import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.requests.IsGetRequest
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.updates.AddUpdateListenerAction
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.datastore.shared.updates.RemoveAllUpdateListenersAction
import maryk.datastore.shared.updates.RemoveUpdateListenerAction
import maryk.datastore.shared.updates.UpdateListenerForGet
import maryk.datastore.shared.updates.UpdateListenerForScan
import maryk.datastore.shared.updates.startProcessUpdateFlow
import kotlin.coroutines.CoroutineContext

/**
 * Abstract DataStore implementation that takes care of the HLC clock
 */
abstract class AbstractDataStore(
    final override val dataModelsById: Map<UInt, IsRootDataModel>,
    coroutineContext: CoroutineContext,
): IsDataStore, CoroutineScope {
    override val coroutineContext = coroutineContext + SupervisorJob() + CoroutineName("MarykDataStore")

    init {
        dataModelsById[0u]?.let {
            throw StorageException("Model 0 is reserved for Meta Data and cannot be used")
        }
    }

    final override val dataModelIdsByString: Map<String, UInt> = dataModelsById.map { (index, dataModel) ->
        Pair(dataModel.Meta.name, index)
    }.toMap()

    private val initIsDone: AtomicBoolean = atomic(false)

    protected val storeActorHasStarted = CompletableDeferred<Unit>()
    /** StoreActor to send actions to.*/
    protected val storeChannel = Channel<StoreAction<*, *, *>>(capacity = 64)

    private val updateSharedFlowHasStarted = CompletableDeferred<Unit>()
    val updateSharedFlow: MutableSharedFlow<IsUpdateAction> = MutableSharedFlow(extraBufferCapacity = 64)

    open fun startFlows() {
        this.launch {
            startProcessUpdateFlow(updateSharedFlow, updateSharedFlowHasStarted)
        }
    }

    private suspend fun waitForInit() {
        if (!initIsDone.value) {
            storeActorHasStarted.await()
            updateSharedFlowHasStarted.await()
            initIsDone.value = true
        }
    }

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP {
        waitForInit()
        assertModelReady(getDataModelId(request.dataModel))

        val response = CompletableDeferred<RP>()

        storeChannel.send(
            StoreAction(request, response)
        )

        return response.await()
    }

    override suspend fun <DM : IsRootDataModel> processUpdate(
        updateResponse: UpdateResponse<DM>
    ): ProcessResponse<DM> {
        waitForInit()
        assertModelReady(getDataModelId(updateResponse.dataModel))

        val response = CompletableDeferred<ProcessResponse<DM>>()

        storeChannel.send(
            StoreAction(updateResponse, response)
        )

        return response.await()
    }

    override suspend fun <DM : IsRootDataModel, RQ: IsFetchRequest<DM, RP>, RP: IsDataResponse<DM>> executeFlow(
        request: RQ
    ): Flow<IsUpdateResponse<DM>> {
        if (request.toVersion != null) {
            throw RequestException("Cannot use toVersion on an executeFlow request")
        }

        waitForInit()

        val dataModelId = getDataModelId(request.dataModel)
        assertModelReady(dataModelId)

        val response = execute(request)

        val listener = request.createUpdateListener(response)

        updateSharedFlow.emit(AddUpdateListenerAction(dataModelId, listener))
        onUpdateListenerAdded(dataModelId)

        return listener.getFlow().onCompletion {
            updateSharedFlow.emit(RemoveUpdateListenerAction(dataModelId, listener))
            onUpdateListenerRemoved(dataModelId)
        }
    }

    protected open fun onUpdateListenerAdded(dataModelId: UInt) {}
    protected open fun onUpdateListenerRemoved(dataModelId: UInt) {}
    protected open fun onAllUpdateListenersRemoved() {}
    protected open fun assertModelReady(dataModelId: UInt) {}

    /** Get [dataModel] id to identify it for storage */
    fun getDataModelId(dataModel: IsRootDataModel) =
        dataModelIdsByString[dataModel.Meta.name] ?:
        throw DefNotFoundException("DataStore not found ${dataModel.Meta.name}")

    override suspend fun close() {
        val job = this@AbstractDataStore.coroutineContext[Job]
        storeChannel.close()
        job?.cancelAndJoin()
    }

    override suspend fun closeAllListeners() {
        updateSharedFlow.emit(RemoveAllUpdateListenersAction)
        onAllUpdateListenersRemoved()
    }

    suspend fun use(block: suspend AbstractDataStore.() -> Unit) {
        try {
            block()
        } finally {
            close()
        }
    }
}

/** Creates update listener for request with [response] */
private fun <DM: IsRootDataModel, RP: IsDataResponse<DM>> IsFetchRequest<DM, RP>.createUpdateListener(
    response: RP
) =
    when (this) {
        is IsScanRequest<DM, RP> -> {
            UpdateListenerForScan(
                request = this,
                scanRange = this.dataModel.createScanRange(this.where, this.startKey?.bytes, this.includeStart),
                response = response
            )
        }
        is IsGetRequest<DM, RP> -> {
            UpdateListenerForGet(
                request = this,
                response = response
            )
        }
        else -> throw RequestException("Unsupported request type for update listener: $this")
    }

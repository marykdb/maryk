package maryk.datastore.shared

import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.requests.IsGetRequest
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.shared.updates.AddUpdateListenerAction
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.datastore.shared.updates.RemoveAllUpdateListenersAction
import maryk.datastore.shared.updates.RemoveUpdateListenerAction
import maryk.datastore.shared.updates.UpdateListenerForGet
import maryk.datastore.shared.updates.UpdateListenerForScan
import maryk.datastore.shared.updates.startProcessUpdateFlow

/**
 * Abstract DataStore implementation that takes care of the HLC clock
 */
abstract class AbstractDataStore(
    final override val dataModelsById: Map<UInt, RootDataModel<*, *>>
): IsDataStore, CoroutineScope {
    override val coroutineContext = DISPATCHER + SupervisorJob()

    final override val dataModelIdsByString: Map<String, UInt> = dataModelsById.map { (index, dataModel) ->
        Pair(dataModel.name, index)
    }.toMap()

    private val initIsDone: AtomicBoolean = atomic(false)

    protected val storeActorHasStarted = CompletableDeferred<Unit>()
    /** StoreActor to send actions to.*/
    protected val storeFlow = MutableSharedFlow<StoreAction<*, *, *, *>>(extraBufferCapacity = 64)

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

    override suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP {
        waitForInit()

        val response = CompletableDeferred<RP>()

        storeFlow.emit(
            StoreAction(request, response)
        )

        return response.await()
    }

    override suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processUpdate(
        updateResponse: UpdateResponse<DM, P>
    ): ProcessResponse<DM> {
        waitForInit()

        val response = CompletableDeferred<ProcessResponse<DM>>()

        storeFlow.emit(
            StoreAction(updateResponse, response)
        )

        return response.await()
    }

    override suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, RQ: IsFetchRequest<DM, P, RP>, RP: IsDataResponse<DM, P>> executeFlow(
        request: RQ
    ): Flow<IsUpdateResponse<DM, P>> {
        if (request.toVersion != null) {
            throw RequestException("Cannot use toVersion on an executeFlow request")
        }

        waitForInit()

        val dataModelId = getDataModelId(request.dataModel)

        val response = execute(request)

        val listener = request.createUpdateListener(response)

        updateSharedFlow.emit(AddUpdateListenerAction(dataModelId, listener))

        return listener.getFlow().onCompletion {
            updateSharedFlow.emit(RemoveUpdateListenerAction(dataModelId, listener))
        }
    }

    /** Get [dataModel] id to identify it for storage */
    fun getDataModelId(dataModel: IsRootValuesDataModel<*>) =
        dataModelIdsByString[dataModel.name] ?:
        throw DefNotFoundException("DataStore not found ${dataModel.name}")

    override fun close() {
        this.cancel()
    }

    override suspend fun closeAllListeners() {
        updateSharedFlow.emit(RemoveAllUpdateListenersAction)
    }
}

/** Creates update listener for request with [response] */
private fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions, RP: IsDataResponse<DM, P>> IsFetchRequest<DM, P, RP>.createUpdateListener(
    response: RP
) =
    when (this) {
        is IsScanRequest<DM, P, RP> -> {
            UpdateListenerForScan(
                request = this,
                scanRange = this.dataModel.createScanRange(this.where, this.startKey?.bytes, this.includeStart),
                response = response
            )
        }
        is IsGetRequest<DM, P, RP> -> {
            UpdateListenerForGet(
                request = this,
                response = response
            )
        }
        else -> throw RequestException("Unsupported request type for update listener: $this")
    }

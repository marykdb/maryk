package maryk.datastore.shared

import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsGetRequest
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.updates.ActivatePendingUpdateListenerAction
import maryk.datastore.shared.updates.FlowSnapshotBoundary
import maryk.datastore.shared.updates.FlowUpdate
import maryk.datastore.shared.updates.FailAllUpdateListenersAction
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.datastore.shared.updates.PendingUpdateListener
import maryk.datastore.shared.updates.AddPendingUpdateListenerAction
import maryk.datastore.shared.updates.RemoveAllUpdateListenersAction
import maryk.datastore.shared.updates.RemovePendingUpdateListenerAction
import maryk.datastore.shared.updates.RemoveUpdateListenerAction
import maryk.datastore.shared.updates.SetPendingListenerBoundaryAction
import maryk.datastore.shared.updates.UpdateListenerForGet
import maryk.datastore.shared.updates.UpdateListenerForScan
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.startProcessUpdateFlow
import kotlin.coroutines.CoroutineContext

/**
 * Abstract DataStore implementation that takes care of the HLC clock
 */
abstract class AbstractDataStore(
    dataModelsById: Map<UInt, IsRootDataModel>,
    coroutineContext: CoroutineContext,
    protected val maxConcurrentReads: Int = 1,
    readWorkerCoroutineContext: CoroutineContext = coroutineContext,
): IsDataStore, CoroutineScope {
    init {
        require(maxConcurrentReads > 0) { "maxConcurrentReads must be greater than zero" }
    }

    override val coroutineContext = coroutineContext + SupervisorJob() + CoroutineName("MarykDataStore")
    private val readCoroutineContext = readWorkerCoroutineContext
    private val localEmissionSequence = atomic(0L)

    private val dataModelRegistry = validatedDataModelRegistry(dataModelsById)
    final override val dataModelsById = dataModelRegistry.dataModelsById
    final override val dataModelIdsByString = dataModelRegistry.dataModelIdsByString

    private val initIsDone: AtomicBoolean = atomic(false)
    private val isClosed: AtomicBoolean = atomic(false)
    private val updateProcessorFailure = atomic<Throwable?>(null)
    private val pendingResponsesMutex = Mutex()
    private val pendingResponses = mutableSetOf<CompletableDeferred<*>>()
    private val mutationBarrier = Mutex()
    private val committedVersion = atomic(HLC().timestamp)

    protected val storeActorHasStarted = CompletableDeferred<Unit>()
    /** StoreActor to send actions to.*/
    protected val storeChannel = Channel<StoreAction<*, *, *>>(capacity = 64)

    /** Reserve the exclusive upper boundary used by historic-change reads. */
    protected suspend fun captureLocalSnapshotVersion(): ULong = mutationBarrier.withLock {
        committedVersion.update { current -> if (current == ULong.MAX_VALUE) current else current + 1uL }
        committedVersion.value
    }

    /** Publish an upper mutation boundary before processing so snapshot capture cannot lag its response. */
    protected fun observeCommittedVersion(version: ULong) {
        committedVersion.update { current -> maxOf(current, version) }
    }

    /** Generate a mutation version strictly beyond captured local snapshot boundaries. */
    protected fun nextMutationClock(clock: HLC, observedVersion: ULong? = null): HLC =
        clock.calculateMaxTimeStamp(HLC(maxOf(committedVersion.value, observedVersion ?: 0uL)))

    private val updateSharedFlowHasStarted = CompletableDeferred<Unit>()
    private val mutableUpdateSharedFlow: MutableSharedFlow<IsUpdateAction> =
        MutableSharedFlow(extraBufferCapacity = 64)
    val updateSharedFlow: Flow<IsUpdateAction> get() = mutableUpdateSharedFlow

    open fun startFlows() {
        this.launch {
            startProcessUpdateFlow(mutableUpdateSharedFlow, updateSharedFlowHasStarted) { error ->
                updateProcessorFailure.value = error
                failPendingResponses(error)
            }
        }
    }

    private suspend fun waitForInit() {
        ensureOpen()
        if (!initIsDone.value) {
            storeActorHasStarted.await()
            updateSharedFlowHasStarted.await()
            mutableUpdateSharedFlow.subscriptionCount.first { it > 0 }
            initIsDone.value = true
        }
        ensureOpen()
    }

    private fun ensureOpen() {
        updateProcessorFailure.value?.let { failure ->
            throw StorageException("DataStore update processor failed", failure)
        }
        if (isClosed.value) {
            throw StorageException("DataStore is closed")
        }
    }

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP {
        waitForInit()
        assertModelReady(getDataModelId(request.dataModel))

        val response = CompletableDeferred<RP>()

        trackPendingResponse(response)
        try {
            storeChannel.send(
                StoreAction(request, response)
            )
            return response.await()
        } catch (error: Throwable) {
            response.completeExceptionally(error)
            throw error
        } finally {
            untrackPendingResponse(response)
        }
    }

    override suspend fun <DM : IsRootDataModel> processUpdate(
        updateResponse: UpdateResponse<DM>
    ): ProcessResponse<DM> {
        waitForInit()
        assertModelReady(getDataModelId(updateResponse.dataModel))

        val response = CompletableDeferred<ProcessResponse<DM>>()

        trackPendingResponse(response)
        try {
            storeChannel.send(
                StoreAction(updateResponse, response)
            )
            return response.await()
        } catch (error: Throwable) {
            response.completeExceptionally(error)
            throw error
        } finally {
            untrackPendingResponse(response)
        }
    }

    override suspend fun <DM : IsRootDataModel, RQ: IsFlowRequest<DM, RP>, RP: IsDataResponse<DM>> executeFlow(
        request: RQ
    ): Flow<IsUpdateResponse<DM>> {
        if (request.toVersion != null) {
            throw RequestException("Cannot use toVersion on an executeFlow request")
        }

        waitForInit()
        assertModelReady(getDataModelId(request.dataModel))

        return flow {
            waitForInit()

            val dataModelId = getDataModelId(request.dataModel)
            assertModelReady(dataModelId)

            val pendingListener = PendingUpdateListener()
            var listenerAddedToStore = false
            var listenerCleanupComplete = false
            try {
                val response = CompletableDeferred<RP>()
                val snapshotBoundary = CompletableDeferred<FlowSnapshotBoundary>()
                trackPendingResponse(response)
                val initialResponse = try {
                    storeChannel.send(
                        StoreAction(
                            request = request,
                            response = response,
                            onBeforeReadContext = {
                                val pendingListenerAdded = CompletableDeferred<Unit>()
                                awaitPendingResponse(pendingListenerAdded) {
                                    mutableUpdateSharedFlow.emit(
                                        AddPendingUpdateListenerAction(
                                            dataModelId,
                                            pendingListener,
                                            pendingListenerAdded,
                                        )
                                    )
                                }
                            },
                            onFlowSnapshotBoundary = { boundary ->
                                val boundarySet = CompletableDeferred<Unit>()
                                awaitPendingResponse(boundarySet) {
                                    mutableUpdateSharedFlow.emit(
                                        SetPendingListenerBoundaryAction(
                                            dataModelId,
                                            pendingListener,
                                            boundary,
                                            boundarySet,
                                        )
                                    )
                                }
                                snapshotBoundary.complete(boundary)
                            },
                        )
                    )
                    response.await()
                } finally {
                    untrackPendingResponse(response)
                }
                val boundary = snapshotBoundary.await()
                val listener = request.createUpdateListener(initialResponse)
                val listenerActivated = CompletableDeferred<Unit>()
                awaitPendingResponse(listenerActivated) {
                    mutableUpdateSharedFlow.emit(
                        ActivatePendingUpdateListenerAction(
                            dataModelId,
                            pendingListener,
                            listener,
                            boundary,
                            listenerActivated,
                        ) {
                            onUpdateListenerActivatedBeforeCompletion(dataModelId)
                        }
                    )
                }
                onUpdateListenerAdded(dataModelId)
                listenerAddedToStore = true

                listener.getFlow().onCompletion {
                    if (isClosed.value) return@onCompletion

                    withContext(NonCancellable) {
                        if (isClosed.value) return@withContext

                        val listenerRemoved = CompletableDeferred<Unit>()
                        awaitPendingResponse(listenerRemoved) {
                            mutableUpdateSharedFlow.emit(RemoveUpdateListenerAction(dataModelId, listener, listenerRemoved))
                        }
                        onUpdateListenerRemoved(dataModelId)
                        listenerCleanupComplete = true
                    }
                }.collect { emit(it) }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    if (!isClosed.value && !listenerCleanupComplete) {
                        val listenerRemoved = CompletableDeferred<Unit>()
                        awaitPendingResponse(listenerRemoved) {
                            mutableUpdateSharedFlow.emit(RemovePendingUpdateListenerAction(dataModelId, pendingListener, listenerRemoved))
                        }
                        if (listenerAddedToStore) {
                            onUpdateListenerRemoved(dataModelId)
                        }
                    }
                }
                throw error
            }
        }
    }

    protected open fun onUpdateListenerAdded(dataModelId: UInt) {}
    protected open suspend fun onUpdateListenerActivatedBeforeCompletion(dataModelId: UInt) {}
    protected open fun onUpdateListenerRemoved(dataModelId: UInt) {}
    protected open fun onAllUpdateListenersRemoved() {}
    protected open suspend fun onBeforeFlowSnapshotBoundary() {}
    protected open fun assertModelReady(dataModelId: UInt) {}

    /** Allocate an update position before its backend publishes it to the listener actor. */
    protected fun prepareFlowUpdate(
        update: Update<out IsRootDataModel>,
        foundationDbCommitVersion: Long? = null,
    ): FlowUpdate {
        @Suppress("UNCHECKED_CAST")
        val rootUpdate = update as Update<IsRootDataModel>
        return FlowUpdate(
            update = rootUpdate,
            localEmissionSequence = localEmissionSequence.incrementAndGet(),
            foundationDbCommitVersion = foundationDbCommitVersion,
        )
    }

    protected suspend fun emitFlowUpdate(
        update: Update<out IsRootDataModel>,
        foundationDbCommitVersion: Long? = null,
    ) {
        mutableUpdateSharedFlow.emit(prepareFlowUpdate(update, foundationDbCommitVersion))
    }

    protected suspend fun emitFlowUpdate(update: FlowUpdate) {
        mutableUpdateSharedFlow.emit(update)
    }

    protected fun createFlowSnapshotBoundary(foundationDbReadVersion: Long? = null) =
        FlowSnapshotBoundary(
            localEmissionSequence = localEmissionSequence.value,
            foundationDbReadVersion = foundationDbReadVersion,
        )

    /** Consume store actions with bounded reads and write exclusion. */
    protected suspend fun processStoreActions(
        processAction: suspend (StoreAction<*, *, *>) -> Unit,
    ) = processStoreActions<Unit>(
        createReadContext = null,
        closeReadContext = {},
    ) { action, _ ->
        processAction(action)
    }

    /**
     * Consume store actions with bounded reads. A prepared native read context allows
     * mutations to overlap reads because it fixes the database state before dispatch.
     */
    protected suspend fun <ReadContext : Any> processStoreActions(
        createReadContext: (suspend () -> ReadContext)?,
        closeReadContext: suspend (ReadContext) -> Unit,
        createFlowSnapshotBoundary: (ReadContext?) -> FlowSnapshotBoundary = {
            this@AbstractDataStore.createFlowSnapshotBoundary()
        },
        processAction: suspend (StoreAction<*, *, *>, ReadContext?) -> Unit,
    ) {
        val readSemaphore = Semaphore(maxConcurrentReads)
        val readJobs = mutableListOf<Job>()

        suspend fun drainReads() {
            readJobs.joinAll()
            readJobs.clear()
        }

        for (action in storeChannel) {
            readJobs.removeAll { it.isCompleted }
            if (action.request.requestExecutionKind == RequestExecutionKind.Read) {
                readSemaphore.acquire()
                try {
                    action.onBeforeReadContext?.invoke()
                } catch (e: CancellationException) {
                    readSemaphore.release()
                    throw e
                } catch (e: Throwable) {
                    readSemaphore.release()
                    e.rethrowIfFatal()
                    action.response.completeExceptionally(e)
                    continue
                }
                val readContext = try {
                    createReadContext?.invoke()
                } catch (e: CancellationException) {
                    readSemaphore.release()
                    throw e
                } catch (e: Throwable) {
                    readSemaphore.release()
                    e.rethrowIfFatal()
                    action.response.completeExceptionally(e)
                    continue
                }
                try {
                    onBeforeFlowSnapshotBoundary()
                } catch (e: CancellationException) {
                    readSemaphore.release()
                    throw e
                } catch (e: Throwable) {
                    readSemaphore.release()
                    e.rethrowIfFatal()
                    action.response.completeExceptionally(e)
                    continue
                }
                try {
                    action.onFlowSnapshotBoundary?.invoke(createFlowSnapshotBoundary(readContext))
                } catch (e: Throwable) {
                    e.rethrowIfFatal()
                    action.response.completeExceptionally(e)
                    if (readContext != null) {
                        try {
                            withContext(NonCancellable) {
                                closeReadContext(readContext)
                            }
                        } catch (closeError: Throwable) {
                            closeError.rethrowIfFatal()
                        }
                    }
                    readSemaphore.release()
                    continue
                }
                readJobs += launch(readCoroutineContext) {
                    try {
                        processAction(action, readContext)
                    } finally {
                        if (readContext != null) {
                            try {
                                withContext(NonCancellable) {
                                    closeReadContext(readContext)
                                }
                            } catch (e: Throwable) {
                                e.rethrowIfFatal()
                                action.response.completeExceptionally(e)
                            }
                        }
                        readSemaphore.release()
                    }
                }
            } else {
                if (createReadContext == null) {
                    drainReads()
                }
                mutationBarrier.withLock {
                    processAction(action, null)
                }
            }
        }
        drainReads()
    }

    /** Get [dataModel] id to identify it for storage */
    fun getDataModelId(dataModel: IsRootDataModel) =
        dataModelIdsByString[dataModel.Meta.name] ?:
        throw DefNotFoundException("DataStore not found ${dataModel.Meta.name}")

    override suspend fun close() {
        if (!startClosingDataStore()) return
        cancelAndJoinDataStoreScope()
    }

    override suspend fun closeAllListeners() {
        if (isClosed.value) return

        waitForInit()

        if (isClosed.value) return

        val listenersRemoved = CompletableDeferred<Unit>()
        awaitPendingResponse(listenersRemoved) {
            mutableUpdateSharedFlow.emit(RemoveAllUpdateListenersAction(listenersRemoved))
        }
        onAllUpdateListenersRemoved()
    }

    /** Fail live listeners when a backend cannot safely continue an incremental stream. */
    protected suspend fun failAllListeners(cause: Throwable) {
        if (isClosed.value) return
        mutableUpdateSharedFlow.emit(FailAllUpdateListenersAction(cause))
        onAllUpdateListenersRemoved()
    }

    suspend fun use(block: suspend AbstractDataStore.() -> Unit) {
        try {
            block()
        } finally {
            close()
        }
    }

    private suspend fun trackPendingResponse(response: CompletableDeferred<*>) {
        pendingResponsesMutex.withLock {
            ensureOpen()
            pendingResponses += response
        }
    }

    private suspend fun untrackPendingResponse(response: CompletableDeferred<*>) {
        pendingResponsesMutex.withLock {
            pendingResponses -= response
        }
    }

    private suspend fun failPendingResponses(error: Throwable) {
        val responses = pendingResponsesMutex.withLock {
            pendingResponses.toList().also { pendingResponses.clear() }
        }
        responses.forEach { it.completeExceptionally(error) }
    }

    protected suspend fun startClosingDataStore(): Boolean {
        if (isClosed.getAndSet(true)) return false

        val closeError = StorageException("DataStore is closed")
        storeChannel.close()
        storeActorHasStarted.completeExceptionally(closeError)
        updateSharedFlowHasStarted.completeExceptionally(closeError)
        failPendingResponses(closeError)

        return true
    }

    protected suspend fun cancelAndJoinDataStoreScope() {
        this@AbstractDataStore.coroutineContext[Job]?.cancelAndJoin()
    }

    private suspend fun awaitPendingResponse(
        response: CompletableDeferred<Unit>,
        sendAction: suspend () -> Unit,
    ) {
        trackPendingResponse(response)
        try {
            sendAction()
            response.await()
        } catch (error: Throwable) {
            response.completeExceptionally(error)
            throw error
        } finally {
            untrackPendingResponse(response)
        }
    }
}

/** Creates update listener for request with [response] */
private fun <DM: IsRootDataModel, RP: IsDataResponse<DM>> IsFlowRequest<DM, RP>.createUpdateListener(
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

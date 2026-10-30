package maryk.datastore.shared

import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.coroutines.yield
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.TransactionCapabilities
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.AtomicStatus
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsGetRequest
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.createCursor
import maryk.core.query.requests.resolveCursor
import maryk.core.query.requests.scan
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
): IsDataStore, CoroutineScope, AtomicDataStore, TransactionalDataStore, VersionedRestoreDataStore {
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
    private val closeCause = atomic<StorageException?>(null)
    private val isClosed get() = closeCause.value != null
    private val updateProcessorFailure = atomic<Throwable?>(null)
    private val pendingResponsesMutex = Mutex()
    private val pendingResponses = mutableSetOf<CompletableDeferred<*>>()
    private val mutationBarrier = Mutex()
    private val committedVersion = atomic(HLC().timestamp)

    protected val storeActorHasStarted = CompletableDeferred<Unit>()
    /** StoreActor to send actions to.*/
    protected val storeChannel = Channel<StoreAction<*, *, *>>(capacity = 64)

    /** Reserve the exclusive upper boundary used by historic-change reads. */
    protected suspend fun captureLocalSnapshotVersion(): ULong = reserveSnapshotVersion { current ->
        if (current == ULong.MAX_VALUE) current else current + 1uL
    }

    /** Keep the local mutation barrier outside backend writer locks when reserving a durable boundary. */
    protected suspend fun reserveSnapshotVersion(reserve: suspend (ULong) -> ULong): ULong = mutationBarrier.withLock {
        reserve(committedVersion.value).also(::observeCommittedVersion)
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
        closeCause.value?.let { throw it }
    }

    override val supportsAtomicRequests: Boolean get() = false

    override val atomicCapabilities: AtomicCapabilities? get() = null

    override val transactionCapabilities: TransactionCapabilities? get() = null

    override suspend fun executeTransaction(request: TransactionRequest): TransactionResponse {
        if (transactionCapabilities == null) return TransactionResponse(TransactionStatus.Unsupported)
        (request.requests + request.initialGuards).forEach { assertRequestModelReady(getDataModelId(it.dataModel)) }
        return execute(TransactionStoreRequest(request, dataModelsById.values.firstOrNull() ?: EmptyTransactionAnchor))
    }

    protected open suspend fun processTransactionRequest(request: TransactionRequest, version: HLC): TransactionResponse =
        TransactionResponse(TransactionStatus.Unsupported)

    open val supportsVersionedRestore: Boolean get() = false

    override val restoreCapabilities get() = atomicCapabilities.takeIf { supportsVersionedRestore }

    override suspend fun restoreVersion(request: RestoreVersionRequest) {
        waitForInit()
        check(currentTransaction(this) == null) { "Restore is not a transaction operation" }
        validateRestoreVersion(request, restoreCapabilities ?: throw RequestException("Atomic version-group restore is unsupported"))
        for (update in request.updates) {
            assertRequestModelReady(getDataModelId(update.dataModel))
            requireSafeUpdateVersion(update.update)
        }
        execute(request)
    }

    protected open suspend fun processRestoreVersionRequest(request: RestoreVersionRequest) {
        throw RequestException("This datastore does not support atomic version-group restore")
    }

    override suspend fun executeAtomic(request: AtomicRequest): AtomicResponse {
        currentTransaction(this)?.let { return it.executeAtomic(request) }
        if (!supportsAtomicRequests) return AtomicResponse(AtomicStatus.Unsupported)
        (request.requests + request.guards).forEach { assertRequestModelReady(getDataModelId(it.dataModel)) }
        return execute(AtomicStoreRequest(request))
    }

    protected open suspend fun processAtomicRequest(request: AtomicRequest, version: HLC): AtomicResponse =
        AtomicResponse(AtomicStatus.Unsupported)

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ
    ): RP {
        if (request !is TransactionStoreRequest) currentTransaction(this)?.let { return it.execute(request).response }
        waitForInit()
        if (request !is TransactionStoreRequest) assertRequestModelReady(getDataModelId(request.dataModel))

        val response = CompletableDeferred<RP>()

        trackPendingResponse(response)
        try {
            storeChannel.send(
                StoreAction(request, response, null, null, requestExecutionContext())
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
        check(currentTransaction(this) == null) { "Replication is not a transaction operation" }
        waitForInit()
        assertRequestModelReady(getDataModelId(updateResponse.dataModel))
        requireSafeUpdateVersion(updateResponse.update)

        val response = CompletableDeferred<ProcessResponse<DM>>()

        trackPendingResponse(response)
        try {
            storeChannel.send(
                StoreAction(updateResponse, response, null, null, requestExecutionContext())
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
        check(currentTransaction(this) == null) { "Subscriptions are not transaction operations" }
        if (request.toVersion != null) {
            throw RequestException("Cannot use toVersion on an executeFlow request")
        }

        waitForInit()
        assertRequestModelReady(getDataModelId(request.dataModel))

        return flow {
            waitForInit()

            val dataModelId = getDataModelId(request.dataModel)
            assertRequestModelReady(dataModelId)

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
                            request = request.withFullValuesForListener(),
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
                            executionContext = requestExecutionContext(),
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
                    cleanupFlowListener {
                        val listenerRemoved = CompletableDeferred<Unit>()
                        awaitPendingResponse(listenerRemoved) {
                            mutableUpdateSharedFlow.emit(RemoveUpdateListenerAction(dataModelId, listener, listenerRemoved))
                        }
                        onUpdateListenerRemoved(dataModelId)
                        listenerCleanupComplete = true
                    }
                }.collect { emit(it) }
            } catch (error: Throwable) {
                cleanupFlowListener {
                    if (!listenerCleanupComplete) {
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

    /** Closure may fail a pending removal acknowledgement after cleanup has started. */
    private suspend fun cleanupFlowListener(cleanup: suspend () -> Unit) {
        withContext(NonCancellable) {
            if (isClosed) return@withContext
            try {
                cleanup()
            } catch (error: Throwable) {
                // Coroutine stacktrace recovery can copy an exception while
                // retaining the original as its cause. Match this store's
                // shutdown signal, rather than its exception type or message.
                val shutdown = closeCause.value
                if (shutdown == null || error !== shutdown && error.cause !== shutdown) throw error
            }
        }
    }

    protected open fun onUpdateListenerAdded(dataModelId: UInt) {}
    protected open suspend fun onUpdateListenerActivatedBeforeCompletion(dataModelId: UInt) {}
    protected open fun onUpdateListenerRemoved(dataModelId: UInt) {}
    protected open fun onAllUpdateListenersRemoved() {}
    protected open suspend fun onBeforeFlowSnapshotBoundary() {}
    /** Backend hook for coroutine-scoped request permissions such as migration handlers. */
    protected open suspend fun assertRequestModelReady(dataModelId: UInt) = assertModelReady(dataModelId)
    /** Backend hook to carry coroutine-scoped permissions to the request processor. */
    protected open suspend fun requestExecutionContext(): Any? = null
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

    /** A committed write must never leave a live subscription silently missing its updates. */
    protected suspend fun publishCommittedTransactionUpdates(block: suspend () -> Unit) {
        try { block() }
        catch (error: Throwable) {
            withContext(NonCancellable) {
                try { failAllListeners(StorageException("Committed transaction notifications were interrupted", error)) }
                catch (notificationError: Throwable) { error.addSuppressed(notificationError) }
            }
            throw error
        }
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
                    throw e
                } catch (e: Throwable) {
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
                readJobs += launch(readCoroutineContext, start = CoroutineStart.UNDISPATCHED) {
                    try {
                        yield()
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
                    val restore = action.request as? RestoreVersionRequest
                    val transaction = action.request as? TransactionStoreRequest
                    val atomic = action.request as? AtomicStoreRequest
                    if (restore != null) {
                        try {
                            processRestoreVersionRequest(restore)
                            @Suppress("UNCHECKED_CAST")
                            (action.response as CompletableDeferred<RestoreVersionResponse>).complete(RestoreVersionResponse)
                        } catch (error: Throwable) {
                            error.rethrowIfFatal()
                            action.response.completeExceptionally(error)
                        }
                    } else if (transaction != null) {
                        @Suppress("UNCHECKED_CAST")
                        val response = action.response as CompletableDeferred<TransactionResponse>
                        try { response.complete(processTransactionRequest(transaction.transaction, nextMutationClock(HLC()))) }
                        catch (abort: TransactionAbort) { response.complete(abort.response) }
                        catch (abort: AtomicAbort) { response.complete(abort.response.asTransactionResponse()) }
                        catch (error: Throwable) {
                            response.complete(TransactionResponse(if (transaction.transaction.commit) TransactionStatus.OutcomeUnknown else TransactionStatus.Aborted, message = error.message))
                            error.rethrowIfFatal()
                        }
                    } else if (atomic == null) {
                        processAction(action, null)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val response = action.response as CompletableDeferred<AtomicResponse>
                        try {
                            response.complete(processAtomicRequest(atomic.atomic, nextMutationClock(HLC())))
                        } catch (abort: AtomicAbort) {
                            response.complete(abort.response)
                        } catch (error: Throwable) {
                            response.complete(AtomicResponse(AtomicStatus.OutcomeUnknown, message = error.message))
                            error.rethrowIfFatal()
                        }
                    }
                }
            }
        }
        drainReads()
    }

    /** Get [dataModel] id to identify it for storage */
    fun getDataModelId(dataModel: IsRootDataModel) =
        dataModelRegistry.getDataModelId(dataModel)

    override suspend fun close() {
        if (!startClosingDataStore()) return
        cancelAndJoinDataStoreScope()
    }

    override suspend fun closeAllListeners() {
        if (isClosed) return

        waitForInit()

        if (isClosed) return

        val listenersRemoved = CompletableDeferred<Unit>()
        awaitPendingResponse(listenersRemoved) {
            mutableUpdateSharedFlow.emit(RemoveAllUpdateListenersAction(listenersRemoved))
        }
        onAllUpdateListenersRemoved()
    }

    /** Fail live listeners when a backend cannot safely continue an incremental stream. */
    protected suspend fun failAllListeners(cause: Throwable) {
        if (isClosed) return
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
        val closeError = StorageException("DataStore is closed")
        if (!closeCause.compareAndSet(null, closeError)) return false
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
            val continuation = (this as? ScanRequest<DM>)?.resolveCursor()
            UpdateListenerForScan(
                request = this,
                scanRange = this.dataModel.createScanRange(
                    this.where,
                    continuation?.key?.bytes ?: this.startKey?.bytes,
                    continuation == null && this.includeStart,
                ),
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

/**
 * Index listeners need complete initial values to retain ordering independently from the public projection.
 * The listener applies the original projection when emitting the initial values update.
 */
@Suppress("UNCHECKED_CAST")
private fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> RQ.withFullValuesForListener(): RQ =
    when (this) {
        is ScanRequest<*> -> (this as ScanRequest<DM>).let { request ->
            val continuation = request.resolveCursor()
            fun fullScan(cursor: ScanCursor?) = request.dataModel.scan(
                startKey = request.startKey,
                select = null,
                where = request.where,
                order = request.order,
                limit = request.limit,
                includeStart = request.includeStart,
                toVersion = request.toVersion,
                filterSoftDeleted = request.filterSoftDeleted,
                aggregations = request.aggregations,
                allowTableScan = request.allowTableScan,
                cursor = cursor,
            )
            val cursor = continuation?.let {
                fullScan(null).createCursor(Key(it.key.bytes), it.orderKey?.bytes)
            }
            fullScan(cursor) as RQ
        }
        else -> this
    }

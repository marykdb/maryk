package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import maryk.core.models.key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.values.Values
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.FlowUpdate
import maryk.datastore.shared.updates.RemovePendingUpdateListenerAction
import maryk.datastore.shared.updates.UPDATE_LISTENER_MAILBOX_CAPACITY
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RequestExecutionTest {
    @Test
    fun fastReadCanCompleteWhileEarlierReadIsSuspended() {
        runTest {
            withContext(Dispatchers.Default) {
                val store = ConcurrentReadTestStore()
                try {
                    val slow = async { store.execute(SimpleMarykModel.scan()) }
                    store.slowReadStarted.await()

                    val fast = async { store.execute(SimpleMarykModel.get()) }
                    withTimeout(2.seconds) { fast.await() }

                    assertTrue(fast.isCompleted)
                    assertFalse(slow.isCompleted)
                    slow.await()
                } finally {
                    store.close()
                }
            }
        }
    }

    @Test
    fun readsDoNotExceedConfiguredConcurrency() {
        runTest {
            withContext(Dispatchers.Default) {
                val store = BoundedReadTestStore()
                try {
                    val first = async { store.execute(SimpleMarykModel.scan()) }
                    val second = async { store.execute(SimpleMarykModel.get()) }
                    val third = async { store.execute(SimpleMarykModel.scan()) }

                    withTimeout(2.seconds) {
                        store.readStarted.receive()
                        store.readStarted.receive()
                    }
                    assertNull(withTimeoutOrNull(100.milliseconds) { store.readStarted.receive() })

                    store.releaseReads.complete(Unit)
                    first.await()
                    second.await()
                    third.await()
                } finally {
                    store.close()
                }
            }
        }
    }

    @Test
    fun preparedReadContextAllowsMutationAfterSnapshotAcquisition() {
        runTest {
            withContext(Dispatchers.Default) {
                val store = SnapshotReadTestStore()
                try {
                    val read = async { store.execute(SimpleMarykModel.scan()) }
                    store.readStarted.await()

                    val mutation = async {
                        store.execute(
                            SimpleMarykModel.add(
                                SimpleMarykModel.create {
                                    value with "haha"
                                }
                            )
                        )
                    }
                    withTimeout(2.seconds) { store.mutationStarted.await() }

                    assertFalse(read.isCompleted)
                    assertFalse(store.readContextClosed.isCompleted)
                    store.releaseRead.complete(Unit)
                    read.await()
                    mutation.await()
                    store.readContextClosed.await()
                    assertEquals(1, store.createdReadContexts)
                } finally {
                    store.close()
                }
            }
        }
    }

    @Test
    fun boundaryCallbackFailureClosesPreparedReadContext() = runTest {
        val store = BoundaryFailureReadTestStore()
        try {
            assertFailsWith<IllegalStateException> {
                store.executeWithFailingBoundaryCallback()
            }
            withTimeout(2.seconds) { store.readContextClosed.await() }
        } finally {
            store.close()
        }
    }

    @Test
    fun snapshotBoundaryPreparationFailureClosesPreparedReadContext() = runTest {
        val store = SnapshotBoundaryPreparationFailureReadTestStore()
        try {
            assertFailsWith<IllegalStateException> {
                store.execute(SimpleMarykModel.scan())
            }
            withTimeout(2.seconds) { store.readContextClosed.await() }
        } finally {
            store.close()
        }
    }

    @Test
    fun cancellationAfterReadWorkerHandoffClosesPreparedReadContext() = runTest {
        val dispatcher = QueuedReadWorkerDispatcher()
        val store = CancellationBeforeReadWorkerTestStore(dispatcher)
        try {
            store.queueRead()
            dispatcher.dispatched.await()

            val close = async(start = CoroutineStart.UNDISPATCHED) { store.close() }
            dispatcher.runNext()

            close.await()
            store.readContextClosed.await()
            assertFalse(store.readWorkerStarted)
        } finally {
            store.close()
        }
    }

    @Test
    fun blockingBoundedReadWorkerDoesNotBlockMutationDispatch() {
        runTest {
            withContext(Dispatchers.Default) {
                val store = WorkerIsolationTestStore()
                try {
                    val read = async { store.execute(SimpleMarykModel.scan()) }
                    store.readStarted.await()

                    val mutation = async {
                        store.execute(
                            SimpleMarykModel.add(
                                SimpleMarykModel.create { value with "next" }
                            )
                        )
                    }
                    withTimeout(2.seconds) { store.mutationStarted.await() }

                    store.releaseRead.complete(Unit)
                    read.await()
                    mutation.await()
                } finally {
                    store.close()
                }
            }
        }
    }

    @Test
    fun snapshotCaptureWaitsForInFlightMutation() {
        runTest {
            withContext(Dispatchers.Default) {
                val store = SnapshotBarrierTestStore()
                try {
                    store.mutationVersion = store.captureSnapshotVersion() + 10uL
                    val mutation = async {
                        store.execute(
                            SimpleMarykModel.add(
                                SimpleMarykModel.create { value with "pending" }
                            )
                        )
                    }
                    store.mutationStarted.await()

                    val snapshot = async { store.captureSnapshotVersion() }
                    assertNull(withTimeoutOrNull(100.milliseconds) { snapshot.await() })

                    store.releaseMutation.complete(Unit)
                    mutation.await()
                    assertTrue(snapshot.await() > store.mutationVersion)
                } finally {
                    if (!store.releaseMutation.isCompleted) {
                        store.releaseMutation.complete(Unit)
                    }
                    store.close()
                }
            }
        }
    }

    @Test
    fun flowBuffersMutationPublishedDuringSnapshot() {
        runTest {
            withContext(Dispatchers.Default) {
                val store = FlowRegistrationTestStore()
                try {
                    val flow = store.executeFlow(SimpleMarykModel.scan(allowTableScan = true))
                    val responses = async {
                        flow.take(2).toList()
                    }
                    store.initialReadStarted.await()

                    val mutation = store.enqueueConcurrentMutation()
                    store.mutationPublished.await()

                    store.releaseInitialRead.complete(Unit)
                    val updates = withTimeout(2.seconds) { responses.await() }
                    mutation.await()

                    assertIs<InitialValuesUpdate<SimpleMarykModel>>(updates[0]).also {
                        assertTrue(it.values.isEmpty())
                    }
                    assertIs<AdditionUpdate<SimpleMarykModel>>(updates[1]).also {
                        assertEquals("added during registration", it.values { value })
                    }
                } finally {
                    store.releaseInitialRead.complete(Unit)
                    store.close()
                }
            }
        }
    }

    @Test
    fun flowDropsDelayedUpdateAlreadyIncludedInSnapshot() {
        runTest {
            withContext(Dispatchers.Default) {
                val store = FlowRegistrationTestStore().apply {
                    setInitialValue("already in snapshot")
                    emitSnapshotUpdateBeforeResponse = true
                }
                try {
                    val flow = store.executeFlow(SimpleMarykModel.scan(allowTableScan = true))
                    val responses = async {
                        flow.take(2).toList()
                    }
                    store.initialReadStarted.await()
                    store.releaseInitialRead.complete(Unit)
                    store.listenerAdded.await()

                    store.enqueueMutation("after snapshot").await()
                    val updates = withTimeout(2.seconds) { responses.await() }

                    assertIs<InitialValuesUpdate<SimpleMarykModel>>(updates[0]).also {
                        assertEquals("already in snapshot", it.values.single().values { value })
                    }
                    assertIs<AdditionUpdate<SimpleMarykModel>>(updates[1]).also {
                        assertEquals(2uL, it.version)
                        assertEquals("after snapshot", it.values { value })
                    }
                } finally {
                    store.releaseInitialRead.complete(Unit)
                    store.close()
                }
            }
        }
    }

    @Test
    fun prequeuedMutationIsIncludedInFlowSnapshotAndListenerIsRemoved() {
        runTest {
            withContext(Dispatchers.Default) {
                val store = FlowRegistrationTestStore()
                try {
                    val queuedMutation = store.enqueueMutation("before snapshot")
                    val flow = store.executeFlow(SimpleMarykModel.scan(allowTableScan = true))
                    val initial = async {
                        flow.take(1).single()
                    }
                    queuedMutation.await()
                    store.initialReadStarted.await()
                    store.releaseInitialRead.complete(Unit)

                    assertIs<InitialValuesUpdate<SimpleMarykModel>>(initial.await()).also {
                        assertEquals("before snapshot", it.values.single().values { value })
                    }
                    store.listenerRemoved.await()
                } finally {
                    store.releaseInitialRead.complete(Unit)
                    store.close()
                }
            }
        }
    }

    @Test
    fun queuedAheadMutationsDoNotOverflowTheFlowRegistrationBuffer() = runTest {
        withContext(Dispatchers.Default) {
            val store = FlowRegistrationTestStore()
            try {
                val mutations = List(UPDATE_LISTENER_MAILBOX_CAPACITY) { index ->
                    store.enqueueMutation("queued mutation $index")
                }
                val flow = store.executeFlow(SimpleMarykModel.scan(allowTableScan = true))
                val initial = async {
                    flow.take(1).single()
                }
                mutations.forEach { it.await() }
                store.initialReadStarted.await()
                store.releaseInitialRead.complete(Unit)

                assertIs<InitialValuesUpdate<SimpleMarykModel>>(withTimeout(2.seconds) { initial.await() }).also {
                    assertEquals("queued mutation ${UPDATE_LISTENER_MAILBOX_CAPACITY - 1}", it.values.single().values { value })
                }
                store.listenerRemoved.await()
            } finally {
                store.releaseInitialRead.complete(Unit)
                store.close()
            }
        }
    }

    @Test
    fun cancellationDuringActivationRemovesTheActivatedListener() {
        runTest {
            withContext(Dispatchers.Default) {
                val store = FlowRegistrationTestStore().apply { pauseActivation = true }
                try {
                    supervisorScope {
                        val flow = store.executeFlow(SimpleMarykModel.scan(allowTableScan = true))
                        val collector = launch {
                            flow.collect()
                        }
                        store.initialReadStarted.await()
                        store.releaseInitialRead.complete(Unit)
                        store.activationStarted.await()

                        collector.cancel()
                        store.releaseActivation.complete(Unit)
                        collector.join()
                        store.cleanupAfterActivation.await()
                    }
                } finally {
                    store.releaseInitialRead.complete(Unit)
                    store.releaseActivation.complete(Unit)
                    store.close()
                }
            }
        }
    }

    @Test
    fun pendingFlowListenerOverflowFailsWhenItsSnapshotCompletes() {
        runTest {
            withContext(Dispatchers.Default) {
                val store = FlowRegistrationTestStore()
                try {
                    supervisorScope {
                        val flow = store.executeFlow(SimpleMarykModel.scan(allowTableScan = true))
                        val collector = async {
                            flow.collect()
                        }
                        store.initialReadStarted.await()

                        repeat(65) { index ->
                            store.enqueueMutation("pending update $index").await()
                        }
                        store.releaseInitialRead.complete(Unit)

                        assertFailsWith<IllegalStateException> {
                            withTimeout(2.seconds) { collector.await() }
                        }
                    }
                    assertFalse(store.listenerRemoved.isCompleted)
                } finally {
                    store.releaseInitialRead.complete(Unit)
                    store.close()
                }
            }
        }
    }
}

private class FlowRegistrationTestStore : AbstractDataStore(
    dataModelsById = mapOf(1u to SimpleMarykModel),
    coroutineContext = Dispatchers.Default,
) {
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = true
    override val supportsSubReferenceFiltering = true

    val initialReadStarted = CompletableDeferred<Unit>()
    val releaseInitialRead = CompletableDeferred<Unit>()
    val mutationPublished = CompletableDeferred<Unit>()
    val listenerAdded = CompletableDeferred<Unit>()
    val listenerRemoved = CompletableDeferred<Unit>()
    val activationStarted = CompletableDeferred<Unit>()
    val releaseActivation = CompletableDeferred<Unit>()
    val cleanupAfterActivation = CompletableDeferred<Unit>()
    private val key = SimpleMarykModel.key(ByteArray(16))
    private var values: Values<SimpleMarykModel>? = null
    private var version = 0uL
    private var delayedSnapshotUpdate: FlowUpdate? = null
    var emitSnapshotUpdateBeforeResponse = false
    var pauseActivation = false

    fun enqueueConcurrentMutation(): CompletableDeferred<AddResponse<SimpleMarykModel>> {
        return enqueueMutation("added during registration")
    }

    fun enqueueMutation(value: String): CompletableDeferred<AddResponse<SimpleMarykModel>> {
        val response = CompletableDeferred<AddResponse<SimpleMarykModel>>()
        check(
            storeChannel.trySend(
                StoreAction(
                    SimpleMarykModel.add(
                        SimpleMarykModel.create { this.value with value }
                    ),
                    response,
                )
            ).isSuccess
        )
        return response
    }

    fun setInitialValue(value: String) {
        values = SimpleMarykModel.create { this.value with value }
        version = 1uL
        delayedSnapshotUpdate = prepareFlowUpdate(
            Update.Addition(SimpleMarykModel, key, version, requireNotNull(values))
        )
    }

    init {
        startFlows()
    }

    override fun startFlows() {
        super.startFlows()
        launch {
            updateSharedFlow.collect { action ->
                if (action is RemovePendingUpdateListenerAction && action.listener.activatedListener.value != null) {
                    cleanupAfterActivation.complete(Unit)
                }
            }
        }
        launch {
            storeActorHasStarted.complete(Unit)
            processStoreActions(
                createReadContext = { Unit },
                closeReadContext = {},
            ) { action, readContext ->
                when (action.request) {
                    is ScanRequest<*> -> {
                        check(readContext != null)
                        val snapshot = values?.let {
                            ValuesWithMetaData(key, it, version, version, isDeleted = false)
                        }
                        initialReadStarted.complete(Unit)
                        releaseInitialRead.await()
                        if (emitSnapshotUpdateBeforeResponse && snapshot != null) {
                            emitFlowUpdate(requireNotNull(delayedSnapshotUpdate))
                        }
                        @Suppress("UNCHECKED_CAST")
                        (action.response as CompletableDeferred<IsResponse>).complete(
                            ValuesResponse(SimpleMarykModel, listOfNotNull(snapshot))
                        )
                    }
                    is AddRequest<*> -> {
                        check(readContext == null)
                        @Suppress("UNCHECKED_CAST")
                        val nextValues = (action.request as AddRequest<SimpleMarykModel>).objects.single()
                        values = nextValues
                        version++
                        emitFlowUpdate(
                            Update.Addition(
                                SimpleMarykModel,
                                key,
                                version,
                                nextValues,
                            )
                        )
                        mutationPublished.complete(Unit)
                        @Suppress("UNCHECKED_CAST")
                        (action.response as CompletableDeferred<IsResponse>).complete(
                            AddResponse(SimpleMarykModel, emptyList())
                        )
                    }
                    else -> error("Unexpected request ${action.request}")
                }
            }
        }
    }

    override fun onUpdateListenerRemoved(dataModelId: UInt) {
        listenerRemoved.complete(Unit)
    }

    override fun onUpdateListenerAdded(dataModelId: UInt) {
        listenerAdded.complete(Unit)
    }

    override suspend fun onUpdateListenerActivatedBeforeCompletion(dataModelId: UInt) {
        if (pauseActivation) {
            activationStarted.complete(Unit)
            releaseActivation.await()
        }
    }
}

private class SnapshotBarrierTestStore : AbstractDataStore(
    dataModelsById = mapOf(1u to SimpleMarykModel),
    coroutineContext = Dispatchers.Default,
), SnapshotVersionProvider {
    override val keepAllVersions = true
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = true
    override val supportsSubReferenceFiltering = true

    override suspend fun captureSnapshotVersion(): ULong = captureLocalSnapshotVersion()

    var mutationVersion = 0uL
    val mutationStarted = CompletableDeferred<Unit>()
    val releaseMutation = CompletableDeferred<Unit>()

    init {
        startFlows()
    }

    override fun startFlows() {
        super.startFlows()
        launch {
            storeActorHasStarted.complete(Unit)
            processStoreActions { action ->
                check(action.request is AddRequest<*>)
                observeCommittedVersion(mutationVersion)
                mutationStarted.complete(Unit)
                releaseMutation.await()
                @Suppress("UNCHECKED_CAST")
                (action.response as CompletableDeferred<IsResponse>).complete(
                    AddResponse(SimpleMarykModel, emptyList())
                )
            }
        }
    }
}

private class ConcurrentReadTestStore : AbstractDataStore(
    dataModelsById = mapOf(1u to SimpleMarykModel),
    coroutineContext = Dispatchers.Default,
    maxConcurrentReads = 2,
) {
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = true
    override val supportsSubReferenceFiltering = true

    val slowReadStarted = CompletableDeferred<Unit>()

    init {
        startFlows()
    }

    override fun startFlows() {
        super.startFlows()
        launch {
            storeActorHasStarted.complete(Unit)
            processStoreActions { action ->
                if (action.request is ScanRequest<*>) {
                    slowReadStarted.complete(Unit)
                    delay(250)
                } else {
                    check(action.request is GetRequest<*>)
                }
                @Suppress("UNCHECKED_CAST")
                (action.response as CompletableDeferred<IsResponse>).complete(
                    ValuesResponse(SimpleMarykModel, emptyList())
                )
            }
        }
    }
}

private class BoundedReadTestStore : AbstractDataStore(
    dataModelsById = mapOf(1u to SimpleMarykModel),
    coroutineContext = Dispatchers.Default,
    maxConcurrentReads = 2,
) {
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = true
    override val supportsSubReferenceFiltering = true

    val readStarted = Channel<Unit>(Channel.UNLIMITED)
    val releaseReads = CompletableDeferred<Unit>()

    init {
        startFlows()
    }

    override fun startFlows() {
        super.startFlows()
        launch {
            storeActorHasStarted.complete(Unit)
            processStoreActions { action ->
                readStarted.send(Unit)
                releaseReads.await()
                @Suppress("UNCHECKED_CAST")
                (action.response as CompletableDeferred<IsResponse>).complete(
                    ValuesResponse(SimpleMarykModel, emptyList())
                )
            }
        }
    }
}

private class SnapshotReadTestStore : AbstractDataStore(
    dataModelsById = mapOf(1u to SimpleMarykModel),
    coroutineContext = Dispatchers.Default,
    maxConcurrentReads = 2,
) {
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = true
    override val supportsSubReferenceFiltering = true

    var createdReadContexts = 0
    val readStarted = CompletableDeferred<Unit>()
    val mutationStarted = CompletableDeferred<Unit>()
    val releaseRead = CompletableDeferred<Unit>()
    val readContextClosed = CompletableDeferred<Unit>()

    init {
        startFlows()
    }

    override fun startFlows() {
        super.startFlows()
        launch {
            storeActorHasStarted.complete(Unit)
            processStoreActions(
                createReadContext = {
                    createdReadContexts++
                    Unit
                },
                closeReadContext = {
                    readContextClosed.complete(Unit)
                },
            ) { action, readContext ->
                when (action.request) {
                    is ScanRequest<*> -> {
                        check(readContext != null)
                        readStarted.complete(Unit)
                        releaseRead.await()
                        @Suppress("UNCHECKED_CAST")
                        (action.response as CompletableDeferred<IsResponse>).complete(
                            ValuesResponse(SimpleMarykModel, emptyList())
                        )
                    }
                    is AddRequest<*> -> {
                        check(readContext == null)
                        mutationStarted.complete(Unit)
                        @Suppress("UNCHECKED_CAST")
                        (action.response as CompletableDeferred<IsResponse>).complete(
                            AddResponse(SimpleMarykModel, emptyList())
                        )
                    }
                    else -> error("Unexpected request ${action.request}")
                }
            }
        }
    }
}

private class BoundaryFailureReadTestStore : AbstractDataStore(
    dataModelsById = mapOf(1u to SimpleMarykModel),
    coroutineContext = Dispatchers.Default,
) {
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = true
    override val supportsSubReferenceFiltering = true

    val readContextClosed = CompletableDeferred<Unit>()

    init {
        startFlows()
    }

    suspend fun executeWithFailingBoundaryCallback() {
        val response = CompletableDeferred<ValuesResponse<SimpleMarykModel>>()
        storeChannel.send(
            StoreAction(SimpleMarykModel.scan(), response) {
                throw IllegalStateException("boundary callback failed")
            }
        )
        response.await()
    }

    override fun startFlows() {
        super.startFlows()
        launch {
            storeActorHasStarted.complete(Unit)
            processStoreActions(
                createReadContext = { Unit },
                closeReadContext = { readContextClosed.complete(Unit) },
            ) { _, _ ->
                error("Read worker must not start after a boundary callback failure")
            }
        }
    }
}

private class SnapshotBoundaryPreparationFailureReadTestStore : AbstractDataStore(
    dataModelsById = mapOf(1u to SimpleMarykModel),
    coroutineContext = Dispatchers.Default,
) {
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = true
    override val supportsSubReferenceFiltering = true

    val readContextClosed = CompletableDeferred<Unit>()

    init {
        startFlows()
    }

    override suspend fun onBeforeFlowSnapshotBoundary() {
        throw IllegalStateException("snapshot boundary preparation failed")
    }

    override fun startFlows() {
        super.startFlows()
        launch {
            storeActorHasStarted.complete(Unit)
            processStoreActions(
                createReadContext = { Unit },
                closeReadContext = { readContextClosed.complete(Unit) },
            ) { _, _ ->
                error("Read worker must not start after snapshot boundary preparation failure")
            }
        }
    }
}

private class CancellationBeforeReadWorkerTestStore(
    dispatcher: CoroutineDispatcher,
) : AbstractDataStore(
    dataModelsById = mapOf(1u to SimpleMarykModel),
    coroutineContext = Dispatchers.Default,
    readWorkerCoroutineContext = dispatcher,
) {
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = true
    override val supportsSubReferenceFiltering = true

    val readContextClosed = CompletableDeferred<Unit>()
    var readWorkerStarted = false

    init {
        startFlows()
    }

    fun queueRead() {
        check(storeChannel.trySend(StoreAction(SimpleMarykModel.scan(), CompletableDeferred())).isSuccess)
    }

    override fun startFlows() {
        super.startFlows()
        launch {
            storeActorHasStarted.complete(Unit)
            processStoreActions(
                createReadContext = { Unit },
                closeReadContext = { readContextClosed.complete(Unit) },
            ) { _, _ ->
                readWorkerStarted = true
            }
        }
    }
}

private class QueuedReadWorkerDispatcher : CoroutineDispatcher() {
    private val blocks = ArrayDeque<Runnable>()
    val dispatched = CompletableDeferred<Unit>()

    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
        synchronized(blocks) {
            blocks.addLast(block)
        }
        dispatched.complete(Unit)
    }

    fun runNext() {
        val block = synchronized(blocks) {
            blocks.removeFirst()
        }
        block.run()
    }
}

private class WorkerIsolationTestStore : AbstractDataStore(
    dataModelsById = mapOf(1u to SimpleMarykModel),
    coroutineContext = DISPATCHER,
    maxConcurrentReads = 1,
    readWorkerCoroutineContext = DISPATCHER.limitedParallelism(1),
) {
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = true
    override val supportsSubReferenceFiltering = true

    val readStarted = CompletableDeferred<Unit>()
    val mutationStarted = CompletableDeferred<Unit>()
    val releaseRead = CompletableDeferred<Unit>()

    init {
        startFlows()
    }

    override fun startFlows() {
        super.startFlows()
        launch {
            storeActorHasStarted.complete(Unit)
            processStoreActions(
                createReadContext = { Unit },
                closeReadContext = {},
            ) { action, readContext ->
                when (action.request) {
                    is ScanRequest<*> -> {
                        check(readContext != null)
                        readStarted.complete(Unit)
                        releaseRead.await()
                        @Suppress("UNCHECKED_CAST")
                        (action.response as CompletableDeferred<IsResponse>).complete(
                            ValuesResponse(SimpleMarykModel, emptyList())
                        )
                    }
                    is AddRequest<*> -> {
                        check(readContext == null)
                        mutationStarted.complete(Unit)
                        @Suppress("UNCHECKED_CAST")
                        (action.response as CompletableDeferred<IsResponse>).complete(
                            AddResponse(SimpleMarykModel, emptyList())
                        )
                    }
                    else -> error("Unexpected request ${action.request}")
                }
            }
        }
    }
}

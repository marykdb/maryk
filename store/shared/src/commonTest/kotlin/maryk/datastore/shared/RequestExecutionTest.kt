package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.ValuesResponse
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

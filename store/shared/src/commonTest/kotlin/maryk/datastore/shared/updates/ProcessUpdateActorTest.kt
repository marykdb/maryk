package maryk.datastore.shared.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.get
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ProcessUpdateActorTest {
    @Test
    fun removeMissingListenerCompletes() = runTest {
        val key = SimpleMarykModel.key(ByteArray(16))
        val values = SimpleMarykModel.create {
            value with "value"
        }
        val listener = CountingUpdateListener(key, values)
        val listenerRemoved = CompletableDeferred<Unit>()
        val flow = flow {
            emit(RemoveUpdateListenerAction(1u, listener, listenerRemoved))
        }

        TestDataStore.startProcessUpdateFlow(flow, CompletableDeferred())

        assertEquals(Unit, listenerRemoved.await())
    }

    @Test
    fun removedListenerDoesNotReceiveLaterUpdates() = runTest {
        val key = SimpleMarykModel.key(ByteArray(16))
        val values = SimpleMarykModel.create {
            value with "value"
        }
        var processCount = 0
        val listener = CountingUpdateListener(key, values) {
            processCount++
        }
        val listenerAdded = CompletableDeferred<Unit>()
        val listenerRemoved = CompletableDeferred<Unit>()
        val flow = flow {
            emit(AddUpdateListenerAction(1u, listener, listenerAdded))
            emit(RemoveUpdateListenerAction(1u, listener, listenerRemoved))
            emit(Update.Addition(SimpleMarykModel, key, 1uL, values))
        }

        TestDataStore.startProcessUpdateFlow(flow, CompletableDeferred())

        assertEquals(Unit, listenerAdded.await())
        assertEquals(Unit, listenerRemoved.await())
        assertEquals(0, processCount)
    }

    @Test
    fun listenerCancellationIsNotWrapped() = runTest {
        val cancellation = CancellationException("cancel")
        val error = assertFailsWith<CancellationException> {
            processSingleUpdateWithListenerFailure(cancellation)
        }

        assertEquals(cancellation.message, error.message)
    }

    @Test
    fun listenerErrorIsNotWrapped() = runTest {
        val fatal = Error("fatal")
        val error = assertFailsWith<Error> {
            processSingleUpdateWithListenerFailure(fatal)
        }

        assertEquals(fatal.message, error.message)
    }

    @Test
    fun listenerExceptionDoesNotStopOtherListeners() = runTest {
        val key = SimpleMarykModel.key(ByteArray(16))
        val values = SimpleMarykModel.create {
            value with "value"
        }
        val throwingListener = ThrowingUpdateListener(IllegalStateException("failed"), key, values)
        var processCount = 0
        val healthyListener = CountingUpdateListener(key, values) {
            processCount++
        }
        val throwingAdded = CompletableDeferred<Unit>()
        val healthyAdded = CompletableDeferred<Unit>()
        val healthyRemoved = CompletableDeferred<Unit>()
        val flow = flow {
            emit(AddUpdateListenerAction(1u, throwingListener, throwingAdded))
            emit(AddUpdateListenerAction(1u, healthyListener, healthyAdded))
            emit(Update.Addition(SimpleMarykModel, key, 1uL, values))
            yield()
            emit(Update.Addition(SimpleMarykModel, key, 2uL, values))
            yield()
            emit(RemoveUpdateListenerAction(1u, healthyListener, healthyRemoved))
        }

        TestDataStore.startProcessUpdateFlow(flow, CompletableDeferred())

        assertEquals(Unit, throwingAdded.await())
        assertEquals(Unit, healthyAdded.await())
        assertEquals(Unit, healthyRemoved.await())
        assertEquals(2, processCount)
        val listenerError = withTimeout(1.seconds) {
            assertFailsWith<UpdateListenerProcessingException> {
                throwingListener.getFlow().collect()
            }
        }
        assertIs<IllegalStateException>(listenerError.cause)
    }

    @Test
    fun listenerCanBeRegisteredAfterAnotherListenerFails() = runTest {
        val key = SimpleMarykModel.key(ByteArray(16))
        val values = SimpleMarykModel.create {
            value with "value"
        }
        val throwingListener = ThrowingUpdateListener(IllegalStateException("failed"), key, values)
        var processCount = 0
        val laterListener = CountingUpdateListener(key, values) {
            processCount++
        }
        val throwingAdded = CompletableDeferred<Unit>()
        val laterAdded = CompletableDeferred<Unit>()
        val laterRemoved = CompletableDeferred<Unit>()
        val flow = flow {
            emit(AddUpdateListenerAction(1u, throwingListener, throwingAdded))
            emit(Update.Addition(SimpleMarykModel, key, 1uL, values))
            yield()
            emit(AddUpdateListenerAction(1u, laterListener, laterAdded))
            emit(Update.Addition(SimpleMarykModel, key, 2uL, values))
            yield()
            emit(RemoveUpdateListenerAction(1u, laterListener, laterRemoved))
        }

        TestDataStore.startProcessUpdateFlow(flow, CompletableDeferred())

        assertEquals(Unit, laterAdded.await())
        assertEquals(Unit, laterRemoved.await())
        assertEquals(1, processCount)
    }

    @Test
    fun blockedListenerOverflowsWithoutBlockingHealthyListener() = runTest {
        val key = SimpleMarykModel.key(ByteArray(16))
        val values = SimpleMarykModel.create {
            value with "value"
        }
        val blockedListener = BlockingUpdateListener(key, values)
        var processCount = 0
        val healthyListener = CountingUpdateListener(key, values) {
            processCount++
        }
        val blockedAdded = CompletableDeferred<Unit>()
        val healthyAdded = CompletableDeferred<Unit>()
        val healthyRemoved = CompletableDeferred<Unit>()
        val flow = flow {
            emit(AddUpdateListenerAction(1u, blockedListener, blockedAdded))
            emit(AddUpdateListenerAction(1u, healthyListener, healthyAdded))
            repeat(70) { index ->
                emit(Update.Addition(SimpleMarykModel, key, index.toULong(), values))
                yield()
            }
            emit(RemoveUpdateListenerAction(1u, healthyListener, healthyRemoved))
        }

        withTimeout(1.seconds) {
            TestDataStore.startProcessUpdateFlow(flow, CompletableDeferred())
        }

        assertEquals(Unit, blockedAdded.await())
        assertEquals(Unit, healthyAdded.await())
        assertEquals(Unit, healthyRemoved.await())
        assertEquals(70, processCount)
        val error = assertFailsWith<UpdateListenerOverflowException> {
            blockedListener.getFlow().collect()
        }
        assertTrue(error.message.orEmpty().contains("could not keep up"))
    }

    @Test
    fun removalCompletesAfterListenerWorkerStops() = runTest {
        val key = SimpleMarykModel.key(ByteArray(16))
        val values = SimpleMarykModel.create {
            value with "value"
        }
        val listener = ShutdownTrackingUpdateListener(key, values)
        val listenerRemoved = CompletableDeferred<Unit>()
        val flow = flow {
            emit(AddUpdateListenerAction(1u, listener, CompletableDeferred()))
            emit(Update.Addition(SimpleMarykModel, key, 1uL, values))
            listener.started.await()
            emit(RemoveUpdateListenerAction(1u, listener, listenerRemoved))
        }

        val processor = async {
            TestDataStore.startProcessUpdateFlow(flow, CompletableDeferred())
        }
        listener.shutdownStarted.await()

        try {
            assertFalse(listenerRemoved.isCompleted)
        } finally {
            listener.allowShutdown.complete(Unit)
        }
        processor.await()
        assertEquals(Unit, listenerRemoved.await())
        assertEquals(Unit, listener.stopped.await())
    }

    private suspend fun processSingleUpdateWithListenerFailure(error: Throwable) {
        val key = SimpleMarykModel.key(ByteArray(16))
        val values = SimpleMarykModel.create {
            value with "value"
        }
        val listener = ThrowingUpdateListener(error, key, values)
        val listenerAdded = CompletableDeferred<Unit>()
        val flow = flow {
            emit(AddUpdateListenerAction(1u, listener, listenerAdded))
            emit(Update.Addition(SimpleMarykModel, key, 1uL, values))
            yield()
        }

        TestDataStore.startProcessUpdateFlow(flow, CompletableDeferred())
    }

    private class ThrowingUpdateListener(
        private val error: Throwable,
        key: Key<SimpleMarykModel>,
        values: Values<SimpleMarykModel>
    ) : UpdateListener<SimpleMarykModel, IsFlowRequest<SimpleMarykModel, *>>(
        request = SimpleMarykModel.get(key),
        response = ValuesResponse(
            dataModel = SimpleMarykModel,
            values = listOf(
                ValuesWithMetaData(
                    key = key,
                    values = values,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false
                )
            )
        )
    ) {
        override suspend fun process(
            update: Update<SimpleMarykModel>,
            dataStore: IsDataStore
        ) {
            throw error
        }

        override fun addValues(key: Key<SimpleMarykModel>, values: Values<SimpleMarykModel>) = null

        override suspend fun changeOrder(
            change: Update.Change<SimpleMarykModel>,
            changedHandler: suspend (Int?, Boolean) -> Unit
        ) = Unit
    }

    private class CountingUpdateListener(
        key: Key<SimpleMarykModel>,
        values: Values<SimpleMarykModel>,
        private val onProcess: () -> Unit = {},
    ) : UpdateListener<SimpleMarykModel, IsFlowRequest<SimpleMarykModel, *>>(
        request = SimpleMarykModel.get(key),
        response = ValuesResponse(
            dataModel = SimpleMarykModel,
            values = listOf(
                ValuesWithMetaData(
                    key = key,
                    values = values,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false
                )
            )
        )
    ) {
        override suspend fun process(
            update: Update<SimpleMarykModel>,
            dataStore: IsDataStore
        ) {
            onProcess()
        }

        override fun addValues(key: Key<SimpleMarykModel>, values: Values<SimpleMarykModel>) = null

        override suspend fun changeOrder(
            change: Update.Change<SimpleMarykModel>,
            changedHandler: suspend (Int?, Boolean) -> Unit
        ) = Unit
    }

    private class BlockingUpdateListener(
        key: Key<SimpleMarykModel>,
        values: Values<SimpleMarykModel>
    ) : UpdateListener<SimpleMarykModel, IsFlowRequest<SimpleMarykModel, *>>(
        request = SimpleMarykModel.get(key),
        response = ValuesResponse(
            dataModel = SimpleMarykModel,
            values = listOf(
                ValuesWithMetaData(
                    key = key,
                    values = values,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false
                )
            )
        )
    ) {
        private val neverComplete = CompletableDeferred<Unit>()

        override suspend fun process(
            update: Update<SimpleMarykModel>,
            dataStore: IsDataStore
        ) {
            neverComplete.await()
        }

        override fun addValues(key: Key<SimpleMarykModel>, values: Values<SimpleMarykModel>) = null

        override suspend fun changeOrder(
            change: Update.Change<SimpleMarykModel>,
            changedHandler: suspend (Int?, Boolean) -> Unit
        ) = Unit
    }

    private class ShutdownTrackingUpdateListener(
        key: Key<SimpleMarykModel>,
        values: Values<SimpleMarykModel>
    ) : UpdateListener<SimpleMarykModel, IsFlowRequest<SimpleMarykModel, *>>(
        request = SimpleMarykModel.get(key),
        response = ValuesResponse(
            dataModel = SimpleMarykModel,
            values = listOf(
                ValuesWithMetaData(
                    key = key,
                    values = values,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false
                )
            )
        )
    ) {
        val started = CompletableDeferred<Unit>()
        val shutdownStarted = CompletableDeferred<Unit>()
        val allowShutdown = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()

        override suspend fun process(
            update: Update<SimpleMarykModel>,
            dataStore: IsDataStore
        ) {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                shutdownStarted.complete(Unit)
                withContext(NonCancellable) {
                    allowShutdown.await()
                }
                stopped.complete(Unit)
            }
        }

        override fun addValues(key: Key<SimpleMarykModel>, values: Values<SimpleMarykModel>) = null

        override suspend fun changeOrder(
            change: Update.Change<SimpleMarykModel>,
            changedHandler: suspend (Int?, Boolean) -> Unit
        ) = Unit
    }

    internal object TestDataStore : IsDataStore {
        override val dataModelsById = mapOf(1u to SimpleMarykModel)
        override val dataModelIdsByString = mapOf(SimpleMarykModel.Meta.name to 1u)
        override val keepAllVersions = false
        override val keepUpdateHistoryIndex = false
        override val supportsFuzzyQualifierFiltering = false
        override val supportsSubReferenceFiltering = false

        override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
            request: RQ
        ): RP = error("Unexpected execute")

        override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
            request: RQ
        ): Flow<IsUpdateResponse<DM>> = error("Unexpected executeFlow")

        override suspend fun <DM : IsRootDataModel> processUpdate(
            updateResponse: UpdateResponse<DM>
        ): ProcessResponse<DM> = error("Unexpected processUpdate")

        override suspend fun close() = Unit

        override suspend fun closeAllListeners() = Unit
    }
}

package maryk.datastore.shared.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
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
import kotlin.test.assertSame

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

        assertSame(cancellation, error)
    }

    @Test
    fun listenerErrorIsNotWrapped() = runTest {
        val fatal = Error("fatal")
        val error = assertFailsWith<Error> {
            processSingleUpdateWithListenerFailure(fatal)
        }

        assertSame(fatal, error)
    }

    @Test
    fun listenerExceptionIsWrapped() = runTest {
        val cause = IllegalStateException("failed")
        val error = assertFailsWith<RuntimeException> {
            processSingleUpdateWithListenerFailure(cause)
        }

        assertSame(cause, error.cause)
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

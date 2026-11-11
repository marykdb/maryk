package maryk.datastore.shared.updates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.get
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateListenerShutdownTest {
    private class Listener : UpdateListener<SimpleMarykModel, IsFlowRequest<SimpleMarykModel, *>>(
        SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16))),
        ValuesResponse(SimpleMarykModel, emptyList()),
    ) {
        suspend fun enqueue(update: IsUpdateResponse<SimpleMarykModel>) = sendFlow.send(update)
        override suspend fun process(update: Update<SimpleMarykModel>, dataStore: IsDataStore) = Unit
        override fun addValues(key: Key<SimpleMarykModel>, values: Values<SimpleMarykModel>): Int? = null
        override suspend fun changeOrder(change: Update.Change<SimpleMarykModel>, changedHandler: suspend (Int?, Boolean) -> Unit) = Unit
    }

    @Test
    fun closingWhileAnUpdateIsBackpressuredCompletesNormally() = runTest {
        repeat(100) {
            val listener = Listener()
            val initial = CompletableDeferred<Unit>()
            val resumeCollector = CompletableDeferred<Unit>()
            val collector = async(Dispatchers.Default) {
                runCatching {
                    listener.getFlow().buffer(0).collect {
                        initial.complete(Unit)
                        resumeCollector.await()
                    }
                }.exceptionOrNull()
            }
            initial.await()
            // Filling the source channel proves that the forwarding job has consumed
            // one item and is blocked sending it to the rendezvous output channel.
            repeat(65) { version -> listener.enqueue(OrderedKeysUpdate(keys = emptyList(), version = version.toULong())) }
            listener.close()
            resumeCollector.complete(Unit)
            assertNull(collector.await())
        }
    }

    @Test
    fun explicitListenerFailureStillReachesTheCollector() = runTest {
        val listener = Listener()
        val initial = CompletableDeferred<Unit>()
        val failure = IllegalStateException("upstream update failed")
        val collector = async { runCatching { listener.getFlow().collect { initial.complete(Unit) } }.exceptionOrNull() }
        runCurrent()
        initial.await()
        listener.close(failure)
        assertEquals(failure.message, assertIs<IllegalStateException>(collector.await()).message)
    }
}

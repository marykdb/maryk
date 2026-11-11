package maryk.datastore.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoundationDBFutureObservationTest {
    @Test
    fun cancellingAnObserverPreservesSharedWork() = runBlocking {
        val work = CompletableDeferred<Int>()
        val future = CoroutineFdbFuture(work)
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { future.awaitObserved() }
        observer.cancelAndJoin()
        assertFalse(work.isCancelled)
        work.complete(42)
        assertEquals(42, future.await())
    }

    @Test
    fun cancellingAnOwningWaitStillCancelsWork() = runBlocking {
        val work = CompletableDeferred<Int>()
        val future = CoroutineFdbFuture(work)
        val owner = launch(start = CoroutineStart.UNDISPATCHED) { future.await() }
        owner.cancelAndJoin()
        assertTrue(work.isCancelled)
    }
}

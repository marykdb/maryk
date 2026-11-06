package maryk.datastore.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.awaitCancellation
import maryk.core.exceptions.RequestException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class RemoteStateCaptureRegistryTest {
    @Test
    fun cancellationBeforeRegistrationIsBoundedAndPreventsAQueuedCapture() = runStateBackupIntegrationTest {
        val registry = RemoteStateCaptureRegistry(1)
        registry.cancel("queued", "owner", allowPending = true)
        var began = false
        assertFailsWith<RequestException> { registry.capture("queued", "owner", TimeSource.Monotonic.markNow()) { began = true } }
        assertFalse(began)
        assertFailsWith<RequestException> { registry.capture("another", "owner", TimeSource.Monotonic.markNow()) { began = true } }
        assertFalse(began)
    }

    @Test
    fun onlyTheOwnerCanCancelAnActiveCaptureAndCompletedIdsAreNotReused() = runStateBackupIntegrationTest {
        val registry = RemoteStateCaptureRegistry(2)
        val began = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val capture = async {
            runCatching {
                registry.capture("active", "owner", TimeSource.Monotonic.markNow()) {
                    began.complete(Unit)
                    try { awaitCancellation() } finally { finished.complete(Unit) }
                }
            }
        }
        began.await()
        registry.cancel("active", "someone-else", allowPending = true)
        assertFalse(finished.isCompleted)
        assertFailsWith<RequestException> { registry.capture("active", "owner", TimeSource.Monotonic.markNow()) {} }
        registry.cancel("active", "owner", allowPending = false)
        withTimeout(5_000) { finished.await() }
        assertTrue(capture.await().isFailure)
        registry.cancel("active", "owner", allowPending = true)
        assertFailsWith<RequestException> { registry.capture("active", "owner", TimeSource.Monotonic.markNow()) {} }
        registry.capture("fresh", "owner", TimeSource.Monotonic.markNow()) {}
    }
}

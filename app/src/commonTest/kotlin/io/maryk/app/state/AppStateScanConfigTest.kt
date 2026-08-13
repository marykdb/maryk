package io.maryk.app.state

import io.maryk.app.config.StoreConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.statuses.ServerFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStateScanConfigTest {
    @Test
    fun invalidatingAnActiveScanClearsBusyStateAndRejectsItsCompletion() {
        val scanBusyState = ScanBusyState()

        val scan = scanBusyState.start()

        assertTrue(scanBusyState.cancel())
        assertFalse(scanBusyState.isScanning)
        assertFalse(scanBusyState.complete(scan))
    }

    @Test
    fun delayedScanAPaginationCompletionDoesNotClearScanBBusyState() = runBlocking {
        val scanBusyState = ScanBusyState()
        val allowScanACompletion = CompletableDeferred<Unit>()

        val scanA = scanBusyState.start()
        val scanB = scanBusyState.start()

        val completedScanA = async {
            allowScanACompletion.await()
            scanBusyState.complete(scanA)
        }

        allowScanACompletion.complete(Unit)

        assertFalse(completedScanA.await())

        assertTrue(scanBusyState.isScanning)

        assertTrue(scanBusyState.complete(scanB))

        assertFalse(scanBusyState.isScanning)
    }

    @Test
    fun parseScanToVersionTrimsBlankAndValidValues() {
        assertNull(parseScanToVersion(""))
        assertNull(parseScanToVersion("  "))
        assertEquals(123uL, parseScanToVersion(" 123 "))
    }

    @Test
    fun parseScanToVersionRejectsInvalidValues() {
        listOf("abc", "-1", "1.2").forEach { value ->
            assertFailsWith<IllegalArgumentException> {
                parseScanToVersion(value)
            }
        }
    }

    @Test
    fun invalidTimeTravelInputDoesNotResolveToLiveVersion() {
        val state = BrowserState(StoreConnector(), CoroutineScope(SupervisorJob()))

        state.updateTimeTravelEnabled(true)
        state.updateTimeTravelDate("not-a-date")

        assertEquals("Time travel error: enter a valid date and time.", state.timeTravelInputError)
        assertEquals("Time travel error: enter a valid date and time.", state.scanStatus)
        val error = assertFailsWith<IllegalStateException> {
            state.currentTimeTravelVersion()
        }
        assertEquals("Time travel error: enter a valid date and time.", error.message)
    }

    @Test
    fun deleteStatusReportsPerRecordFailure() {
        val result = formatDeleteStatus(
            label = "Example abc",
            hardDelete = false,
            status = ServerFail<IsRootDataModel>("version mismatch"),
        )

        assertEquals("Delete failed: version mismatch", result.message)
        assertEquals(false, result.success)
    }
}

package io.maryk.app.state

import io.maryk.app.config.StoreConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppStateScanConfigTest {
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
}

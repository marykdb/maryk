package io.maryk.app.state

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
}

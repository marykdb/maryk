package io.maryk.cli

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalEnvironmentJvmTest {
    @Test
    fun detectsWindowsConsoleWithoutTerm() {
        assertTrue(isInteractiveTerminal(consolePresent = true, term = null, osName = "Windows 11"))
    }

    @Test
    fun rejectsDumbTerminal() {
        assertFalse(isInteractiveTerminal(consolePresent = true, term = "dumb", osName = "Linux"))
    }

    @Test
    fun rejectsNonWindowsConsoleWithoutTerm() {
        assertFalse(isInteractiveTerminal(consolePresent = true, term = null, osName = "Linux"))
    }
}

package io.maryk.cli

actual fun isInteractiveTerminal(): Boolean {
    val consolePresent = System.console() != null
    val term = System.getenv("TERM")?.lowercase()
    return consolePresent && term != null && term != "dumb"
}

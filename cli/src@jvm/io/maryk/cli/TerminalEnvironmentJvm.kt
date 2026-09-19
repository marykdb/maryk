package io.maryk.cli

actual fun isInteractiveTerminal(): Boolean {
    return isInteractiveTerminal(
        consolePresent = System.console() != null,
        term = System.getenv("TERM"),
        osName = System.getProperty("os.name"),
    )
}

internal fun isInteractiveTerminal(
    consolePresent: Boolean,
    term: String?,
    osName: String,
): Boolean = consolePresent && (
    osName.startsWith("Windows", ignoreCase = true) || (term != null && term.lowercase() != "dumb")
)

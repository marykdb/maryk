package io.maryk.cli

actual fun terminalHeight(): Int {
    val value = System.getenv("LINES")?.toIntOrNull()
    return if (value != null && value > 0) value else DEFAULT_TERMINAL_HEIGHT
}

private const val DEFAULT_TERMINAL_HEIGHT = 24

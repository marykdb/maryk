@file:OptIn(ExperimentalForeignApi::class)

package io.maryk.cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

actual fun terminalHeight(): Int {
    val value = getenv("LINES")?.toKString()?.toIntOrNull()
    return if (value != null && value > 0) value else DEFAULT_TERMINAL_HEIGHT
}

private const val DEFAULT_TERMINAL_HEIGHT = 24

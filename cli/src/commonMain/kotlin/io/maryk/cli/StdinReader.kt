package io.maryk.cli

internal expect fun readStdinBytes(): ByteArray

internal fun readStdinText(): String = readStdinBytes().decodeToString()

internal const val MAX_STDIN_BYTES = 64 * 1024 * 1024

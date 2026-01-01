package io.maryk.cli

internal expect fun readStdinBytes(): ByteArray

internal fun readStdinText(): String = readStdinBytes().decodeToString()

package io.maryk.cli

internal actual fun readStdinBytes(): ByteArray = System.`in`.readBytes()

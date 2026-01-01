@file:OptIn(ExperimentalForeignApi::class)

package io.maryk.cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.STDIN_FILENO
import platform.posix.read

internal actual fun readStdinBytes(): ByteArray {
    val output = ArrayList<Byte>()
    val buffer = ByteArray(4096)
    while (true) {
        val readCount = buffer.usePinned { pinned ->
            read(STDIN_FILENO, pinned.addressOf(0), buffer.size.toULong())
        }.toInt()
        if (readCount <= 0) break
        for (i in 0 until readCount) {
            output.add(buffer[i])
        }
    }
    return output.toByteArray()
}

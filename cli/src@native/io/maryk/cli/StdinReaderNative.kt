@file:OptIn(ExperimentalForeignApi::class)

package io.maryk.cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.EINTR
import platform.posix.STDIN_FILENO
import platform.posix.errno
import platform.posix.read
import platform.posix.strerror

internal actual fun readStdinBytes(): ByteArray {
    val chunks = mutableListOf<ByteArray>()
    var totalSize = 0
    val buffer = ByteArray(4096)
    while (true) {
        val readCount = buffer.usePinned { pinned ->
            read(STDIN_FILENO, pinned.addressOf(0), buffer.size.toULong())
        }.toInt()
        if (readCount < 0 && errno == EINTR) continue
        if (readCount < 0) {
            throw IllegalStateException("Could not read stdin: ${errnoMessage()}")
        }
        if (readCount == 0) break
        if (totalSize + readCount > MAX_STDIN_BYTES) {
            throw IllegalArgumentException("stdin payload exceeds max size: $MAX_STDIN_BYTES bytes")
        }
        chunks += buffer.copyOf(readCount)
        totalSize += readCount
    }
    val output = ByteArray(totalSize)
    var offset = 0
    chunks.forEach { chunk ->
        chunk.copyInto(output, offset)
        offset += chunk.size
    }
    return output
}

private fun errnoMessage(): String = strerror(errno)?.toKString() ?: "errno $errno"

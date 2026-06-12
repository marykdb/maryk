package io.maryk.cli

import java.io.ByteArrayOutputStream

internal actual fun readStdinBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val readCount = System.`in`.read(buffer)
        if (readCount < 0) break
        if (readCount == 0) continue
        if (output.size() + readCount > MAX_STDIN_BYTES) {
            throw IllegalArgumentException("stdin payload exceeds max size: $MAX_STDIN_BYTES bytes")
        }
        output.write(buffer, 0, readCount)
    }
    return output.toByteArray()
}

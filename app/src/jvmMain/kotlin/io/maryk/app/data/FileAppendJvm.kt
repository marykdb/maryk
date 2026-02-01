package io.maryk.app.data

import java.io.File
import java.io.FileOutputStream

internal actual fun appendBytes(path: String, bytes: ByteArray) {
    val file = File(path)
    file.parentFile?.mkdirs()
    FileOutputStream(file, true).use { stream ->
        stream.write(bytes)
    }
}
package io.maryk.app.data

import java.io.File
import java.io.FileOutputStream

internal actual suspend fun writeBufferedFile(
    path: String,
    write: suspend (append: (ByteArray) -> Unit) -> Unit,
) {
    val file = File(path)
    file.parentFile?.mkdirs()
    FileOutputStream(file, false).buffered().use { stream ->
        write(stream::write)
    }
}

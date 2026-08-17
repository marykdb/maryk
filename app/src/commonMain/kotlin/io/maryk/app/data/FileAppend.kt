package io.maryk.app.data

internal expect suspend fun writeBufferedFile(
    path: String,
    write: suspend (append: (ByteArray) -> Unit) -> Unit,
)

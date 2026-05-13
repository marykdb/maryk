@file:OptIn(UnsafeNumber::class)
@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")
package maryk.file

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.posix.F_OK
import platform.posix.O_APPEND
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.access
import platform.posix.close
import platform.posix.mkdir
import platform.posix.open
import platform.posix.read
import platform.posix.stat
import platform.posix.write

private fun fileExists(path: String): Boolean = access(path, F_OK) == 0
private const val maxFileSize = Int.MAX_VALUE.toLong()

private fun createParentDirectories(path: String): Boolean {
    val parentPath = path.substringBeforeLast('/', "")
    if (parentPath.isEmpty()) return true

    val segments = parentPath.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return true

    var current = if (parentPath.startsWith("/")) "/" else ""
    for (segment in segments) {
        current = when {
            current == "/" -> "/$segment"
            current.isEmpty() -> segment
            else -> "$current/$segment"
        }

        if (!fileExists(current)) {
            val result = mkdir(current, 0x1EDu) // 0755
            if (result != 0 && !fileExists(current)) {
                return false
            }
        }
    }

    return true
}

@OptIn(ExperimentalForeignApi::class)
private fun fileSize(path: String): Long {
    return memScoped {
        val st = alloc<stat>()
        val res = stat(path, st.ptr)
        if (res == 0) st.st_size else 0L
    }
}

actual object File {
    @OptIn(ExperimentalForeignApi::class)
    actual fun readText(path: String): String? {
        return readBytes(path)?.decodeToString()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readBytes(path: String): ByteArray? {
        if (!fileExists(path)) return null
        val size = fileSize(path)
        if (size <= 0) return ByteArray(0)
        if (size > maxFileSize) return null
        val fd = open(path, O_RDONLY, 0)
        if (fd < 0) return null
        try {
            val buffer = ByteArray(size.toInt())
            buffer.usePinned { pinned ->
                var offset = 0
                while (offset < buffer.size) {
                    val readBytes = read(fd, pinned.addressOf(offset), (buffer.size - offset).convert())
                    if (readBytes < 0) return null
                    if (readBytes.toLong() == 0L) break
                    offset += readBytes.toInt()
                }
                return if (offset == buffer.size) buffer else buffer.copyOf(offset)
            }
        } finally {
            close(fd)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeText(path: String, contents: String) {
        if (!createParentDirectories(path)) throw IllegalStateException("Could not create parent directories for $path")
        val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x1A4) // 0644
        if (fd < 0) throw IllegalStateException("Could not open file for writing: $path")
        try {
            val bytes = contents.encodeToByteArray()
            writeAll(fd, bytes)
        } finally {
            close(fd)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeBytes(path: String, contents: ByteArray) {
        if (!createParentDirectories(path)) throw IllegalStateException("Could not create parent directories for $path")
        val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x1A4) // 0644
        if (fd < 0) throw IllegalStateException("Could not open file for writing: $path")
        try {
            writeAll(fd, contents)
        } finally {
            close(fd)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun appendText(path: String, contents: String) {
        if (!createParentDirectories(path)) throw IllegalStateException("Could not create parent directories for $path")
        val fd = open(path, O_WRONLY or O_CREAT or O_APPEND, 0x1A4)
        if (fd < 0) throw IllegalStateException("Could not open file for appending: $path")
        try {
            val bytes = contents.encodeToByteArray()
            writeAll(fd, bytes)
        } finally {
            close(fd)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun delete(path: String): Boolean = platform.posix.remove(path) == 0
}

@OptIn(ExperimentalForeignApi::class)
private fun writeAll(fd: Int, bytes: ByteArray) {
    bytes.usePinned { pinned ->
        var offset = 0
        while (offset < bytes.size) {
            val written = write(fd, pinned.addressOf(offset), (bytes.size - offset).convert())
            if (written <= 0) {
                throw IllegalStateException("Could not write file contents")
            }
            offset += written.toInt()
        }
    }
}

@file:OptIn(UnsafeNumber::class)
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
import platform.posix.open
import platform.posix.read
import platform.posix.stat
import platform.posix.write

private fun fileExists(path: String): Boolean = access(path, F_OK) == 0

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
        if (!fileExists(path)) return null
        val size = fileSize(path)
        if (size <= 0) return ""
        val fd = open(path, O_RDONLY, 0)
        if (fd < 0) return null
        try {
            val buffer = ByteArray(size.toInt())
            buffer.usePinned { pinned ->
                val readBytes: ULong = read(fd, pinned.addressOf(0), size.convert()).convert()
                if (readBytes < 0uL) return null
                return buffer.decodeToString(endIndex = readBytes.convert())
            }
        } finally {
            close(fd)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readBytes(path: String): ByteArray? {
        if (!fileExists(path)) return null
        val size = fileSize(path)
        if (size <= 0) return ByteArray(0)
        val fd = open(path, O_RDONLY, 0)
        if (fd < 0) return null
        try {
            val buffer = ByteArray(size.toInt())
            buffer.usePinned { pinned ->
                val readBytes: ULong = read(fd, pinned.addressOf(0), size.convert()).convert()
                if (readBytes < 0uL) return null
                val readCount = readBytes.toInt()
                return if (readCount == buffer.size) buffer else buffer.copyOf(readCount)
            }
        } finally {
            close(fd)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeText(path: String, contents: String) {
        val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x1A4) // 0644
        if (fd < 0) return
        try {
            val bytes = contents.encodeToByteArray()
            bytes.usePinned { pinned ->
                write(fd, pinned.addressOf(0), bytes.size.convert())
            }
        } finally {
            close(fd)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeBytes(path: String, contents: ByteArray) {
        val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x1A4) // 0644
        if (fd < 0) return
        try {
            contents.usePinned { pinned ->
                write(fd, pinned.addressOf(0), contents.size.convert())
            }
        } finally {
            close(fd)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun appendText(path: String, contents: String) {
        val fd = open(path, O_WRONLY or O_CREAT or O_APPEND, 0x1A4)
        if (fd < 0) return
        try {
            val bytes = contents.encodeToByteArray()
            bytes.usePinned { pinned ->
                write(fd, pinned.addressOf(0), bytes.size.convert())
            }
        } finally {
            close(fd)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun delete(path: String): Boolean = platform.posix.remove(path) == 0
}

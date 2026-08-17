package maryk.file

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.windows.CloseHandle
import platform.windows.CREATE_ALWAYS
import platform.windows.CreateDirectoryW
import platform.windows.CreateFileW
import platform.windows.DWORDVar
import platform.windows.DeleteFileW
import platform.windows.FILE_ATTRIBUTE_DIRECTORY
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_END
import platform.windows.FlushFileBuffers
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetFileAttributesW
import platform.windows.GetLastError
import platform.windows.GetFileSizeEx
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LARGE_INTEGER
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.MoveFileExW
import platform.windows.OPEN_ALWAYS
import platform.windows.OPEN_EXISTING
import platform.windows.ReadFile
import platform.windows.SetFilePointerEx
import platform.windows.WriteFile

private const val maxFileSize = Int.MAX_VALUE.toLong()
private const val invalidFileAttributes = UInt.MAX_VALUE

private fun isDirectory(path: String): Boolean {
    val attributes = GetFileAttributesW(path)
    return attributes != invalidFileAttributes && (attributes and FILE_ATTRIBUTE_DIRECTORY.toUInt()) != 0u
}

private fun createParentDirectories(path: String): Boolean {
    for (directory in windowsParentDirectories(path)) {
        val created = CreateDirectoryW(directory, null)
        if (created == 0 && !isDirectory(directory)) {
            return false
        }
    }

    return true
}

private fun isRegularFile(path: String): Boolean {
    val attributes = GetFileAttributesW(path)
    return attributes != invalidFileAttributes && (attributes and FILE_ATTRIBUTE_DIRECTORY.toUInt()) == 0u
}

@OptIn(ExperimentalForeignApi::class)
private fun fileSize(handle: HANDLE?): Long? {
    return memScoped {
        val li = alloc<LARGE_INTEGER>()
        if (GetFileSizeEx(handle, li.ptr) != 0) li.QuadPart else null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun seekToEnd(handle: HANDLE?): Boolean = memScoped {
    val distance = alloc<LARGE_INTEGER>()
    distance.QuadPart = 0
    SetFilePointerEx(handle, distance.readValue(), null, FILE_END.toUInt()) != 0
}

actual object File {
    @OptIn(ExperimentalForeignApi::class)
    actual fun size(path: String): Long? {
        if (!isRegularFile(path)) return null
        val handle: HANDLE? = CreateFileW(
            path,
            GENERIC_READ,
            0u,
            null,
            OPEN_EXISTING.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) return null
        try {
            return fileSize(handle)
        } finally {
            CloseHandle(handle)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readText(path: String): String? {
        return readBytes(path)?.decodeToString()
    }

    actual fun readText(path: String, maxBytes: Int): String? =
        readBytes(path, maxBytes)?.decodeToString()

    @OptIn(ExperimentalForeignApi::class)
    actual fun readBytes(path: String): ByteArray? {
        return readBytes(path, Int.MAX_VALUE)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readBytes(path: String, maxBytes: Int): ByteArray? {
        require(maxBytes >= 0) { "Maximum byte count cannot be negative" }
        if (!isRegularFile(path)) return null
        val handle: HANDLE? = CreateFileW(
            path,
            GENERIC_READ,
            0u,
            null,
            OPEN_EXISTING.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) return null
        try {
            val size = fileSize(handle) ?: return null
            if (size <= 0) return ByteArray(0)
            if (size > minOf(maxFileSize, maxBytes.toLong())) return null
            val buffer = ByteArray(size.toInt())
            buffer.usePinned { pinned ->
                var offset = 0
                while (offset < buffer.size) {
                    memScoped {
                        val read = alloc<DWORDVar>()
                        val chunkSize = (buffer.size - offset).toUInt()
                        if (ReadFile(handle, pinned.addressOf(offset), chunkSize, read.ptr, null) == 0) {
                            return null
                        }
                        if (read.value == 0u) {
                            return if (offset == buffer.size) buffer else buffer.copyOf(offset)
                        }
                        offset += read.value.toInt()
                    }
                }
                return buffer
            }
        } finally {
            CloseHandle(handle)
        }
        return null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readChunks(path: String, chunkSize: Int, onChunk: (ByteArray) -> Unit): Boolean {
        require(chunkSize > 0) { "Chunk size must be positive" }
        if (!isRegularFile(path)) return false
        val handle: HANDLE? = CreateFileW(path, GENERIC_READ, 0u, null, OPEN_EXISTING.toUInt(), FILE_ATTRIBUTE_NORMAL.toUInt(), null)
        if (handle == null || handle == INVALID_HANDLE_VALUE) return false
        try {
            val buffer = ByteArray(chunkSize)
            buffer.usePinned { pinned ->
                while (true) {
                    val count = memScoped {
                        val read = alloc<DWORDVar>()
                        if (ReadFile(handle, pinned.addressOf(0), buffer.size.toUInt(), read.ptr, null) == 0) return false
                        read.value.toInt()
                    }
                    if (count == 0) return true
                    onChunk(buffer.copyOf(count))
                }
            }
        } finally {
            CloseHandle(handle)
        }
        return false
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeText(path: String, contents: String) {
        if (!createParentDirectories(path)) throw IllegalStateException("Could not create parent directories for $path")
        val handle: HANDLE? = CreateFileW(
            path,
            GENERIC_WRITE.toUInt(),
            0u,
            null,
            CREATE_ALWAYS.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) {
            throw IllegalStateException("Could not open file for writing: $path (${GetLastError()})")
        }
        try {
            val bytes = contents.encodeToByteArray()
            writeAll(handle, bytes)
        } finally {
            CloseHandle(handle)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeBytes(path: String, contents: ByteArray) {
        if (!createParentDirectories(path)) throw IllegalStateException("Could not create parent directories for $path")
        val handle: HANDLE? = CreateFileW(
            path,
            GENERIC_WRITE.toUInt(),
            0u,
            null,
            CREATE_ALWAYS.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) {
            throw IllegalStateException("Could not open file for writing: $path (${GetLastError()})")
        }
        try {
            writeAll(handle, contents)
        } finally {
            CloseHandle(handle)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun appendText(path: String, contents: String) {
        if (!createParentDirectories(path)) throw IllegalStateException("Could not create parent directories for $path")
        val handle: HANDLE? = CreateFileW(
            path,
            GENERIC_WRITE.toUInt(),
            0u,
            null,
            OPEN_ALWAYS.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) {
            throw IllegalStateException("Could not open file for appending: $path (${GetLastError()})")
        }
        try {
            if (!seekToEnd(handle)) {
                throw IllegalStateException("Could not seek to end of file for appending: $path (${GetLastError()})")
            }
            val bytes = contents.encodeToByteArray()
            writeAll(handle, bytes)
        } finally {
            CloseHandle(handle)
        }
    }

    actual fun moveReplace(sourcePath: String, destinationPath: String) {
        check(MoveFileExW(sourcePath, destinationPath, MOVEFILE_REPLACE_EXISTING.toUInt()) != 0) {
            "Could not replace $destinationPath with $sourcePath (${GetLastError()})"
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun syncFile(path: String): Boolean {
        val handle = CreateFileW(path, GENERIC_WRITE.toUInt(), 0u, null, OPEN_EXISTING.toUInt(), FILE_ATTRIBUTE_NORMAL.toUInt(), null)
        if (handle == null || handle == INVALID_HANDLE_VALUE) return false
        try {
            return FlushFileBuffers(handle) != 0
        } finally {
            CloseHandle(handle)
        }
    }

    /** Windows does not expose a reliable directory-handle flush through this API. */
    actual fun syncParentDirectory(path: String): Boolean = false

    actual fun delete(path: String): Boolean {
        return DeleteFileW(path) != 0
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun writeAll(handle: HANDLE?, bytes: ByteArray) {
    bytes.usePinned { pinned ->
        var offset = 0
        while (offset < bytes.size) {
            memScoped {
                val written = alloc<DWORDVar>()
                val chunkSize = (bytes.size - offset).toUInt()
                if (WriteFile(handle, pinned.addressOf(offset), chunkSize, written.ptr, null) == 0) {
                    throw IllegalStateException("Could not write file contents")
                }
                if (written.value == 0u) {
                    throw IllegalStateException("Could not write file contents")
                }
                offset += written.value.toInt()
            }
        }
    }
}

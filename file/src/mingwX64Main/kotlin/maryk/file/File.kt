package maryk.file

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.windows.CloseHandle
import platform.windows.CreateDirectoryW
import platform.windows.CreateFileW
import platform.windows.DWORDVar
import platform.windows.DeleteFileW
import platform.windows.ERROR_ALREADY_EXISTS
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_END
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetLastError
import platform.windows.GetFileSizeEx
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LARGE_INTEGER
import platform.windows.OPEN_ALWAYS
import platform.windows.OPEN_EXISTING
import platform.windows.ReadFile
import platform.windows.SetFilePointer
import platform.windows.WriteFile

private fun createParentDirectories(path: String): Boolean {
    val separatorIndex = path.lastIndexOfAny(charArrayOf('\\', '/'))
    if (separatorIndex <= 0) return true

    val parent = path.substring(0, separatorIndex).replace('/', '\\')
    if (parent.isEmpty()) return true

    val rootPrefix = when {
        parent.startsWith("\\\\") -> "\\\\"
        parent.length >= 2 && parent[1] == ':' -> parent.substring(0, 2)
        parent.startsWith("\\") -> "\\"
        else -> ""
    }

    val remainder = when {
        rootPrefix == "\\\\" -> parent.removePrefix("\\\\")
        rootPrefix.length == 2 && rootPrefix[1] == ':' -> parent.removePrefix(rootPrefix).removePrefix("\\")
        rootPrefix == "\\" -> parent.removePrefix("\\")
        else -> parent
    }

    var current = rootPrefix
    for (segment in remainder.split('\\').filter { it.isNotEmpty() }) {
        current = when {
            current.isEmpty() -> segment
            current == "\\\\" -> "\\\\$segment"
            current.endsWith("\\") -> "$current$segment"
            current.length == 2 && current[1] == ':' -> "$current\\$segment"
            else -> "$current\\$segment"
        }

        val created = CreateDirectoryW(current, null)
        if (created == 0 && GetLastError() != ERROR_ALREADY_EXISTS.toUInt()) {
            return false
        }
    }

    return true
}

actual object File {
    @OptIn(ExperimentalForeignApi::class)
    actual fun readText(path: String): String? {
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
            val size = memScoped {
                val li = alloc<LARGE_INTEGER>()
                if (GetFileSizeEx(handle, li.ptr) != 0) li.QuadPart.toInt() else 0
            }
            if (size <= 0) return ""
            val buffer = ByteArray(size)
            buffer.usePinned { pinned ->
                memScoped {
                    val read = alloc<DWORDVar>()
                    if (ReadFile(handle, pinned.addressOf(0), size.toUInt(), read.ptr, null) != 0) {
                        return buffer.decodeToString(endIndex = read.value.toInt())
                    }
                }
            }
        } finally {
            CloseHandle(handle)
        }
        return null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readBytes(path: String): ByteArray? {
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
            val size = memScoped {
                val li = alloc<LARGE_INTEGER>()
                if (GetFileSizeEx(handle, li.ptr) != 0) li.QuadPart.toInt() else 0
            }
            if (size <= 0) return ByteArray(0)
            val buffer = ByteArray(size)
            buffer.usePinned { pinned ->
                memScoped {
                    val read = alloc<DWORDVar>()
                    if (ReadFile(handle, pinned.addressOf(0), size.toUInt(), read.ptr, null) != 0) {
                        val readCount = read.value.toInt()
                        return if (readCount == buffer.size) buffer else buffer.copyOf(readCount)
                    }
                }
            }
        } finally {
            CloseHandle(handle)
        }
        return null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeText(path: String, contents: String) {
        if (!createParentDirectories(path)) return
        val handle: HANDLE? = CreateFileW(
            path,
            GENERIC_WRITE.toUInt(),
            0u,
            null,
            OPEN_ALWAYS.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) return
        try {
            val bytes = contents.encodeToByteArray()
            bytes.usePinned { pinned ->
                memScoped {
                    val written = alloc<DWORDVar>()
                    WriteFile(handle, pinned.addressOf(0), bytes.size.toUInt(), written.ptr, null)
                }
            }
        } finally {
            CloseHandle(handle)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeBytes(path: String, contents: ByteArray) {
        if (!createParentDirectories(path)) return
        val handle: HANDLE? = CreateFileW(
            path,
            GENERIC_WRITE.toUInt(),
            0u,
            null,
            OPEN_ALWAYS.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) return
        try {
            contents.usePinned { pinned ->
                memScoped {
                    val written = alloc<DWORDVar>()
                    WriteFile(handle, pinned.addressOf(0), contents.size.toUInt(), written.ptr, null)
                }
            }
        } finally {
            CloseHandle(handle)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun appendText(path: String, contents: String) {
        if (!createParentDirectories(path)) return
        val handle: HANDLE? = CreateFileW(
            path,
            GENERIC_WRITE.toUInt(),
            0u,
            null,
            OPEN_ALWAYS.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )
        if (handle == null || handle == INVALID_HANDLE_VALUE) return
        try {
            // Move to end
            SetFilePointer(handle, 0, null, FILE_END.toUInt())
            val bytes = contents.encodeToByteArray()
            bytes.usePinned { pinned ->
                memScoped {
                    val written = alloc<DWORDVar>()
                    WriteFile(handle, pinned.addressOf(0), bytes.size.toUInt(), written.ptr, null)
                }
            }
        } finally {
            CloseHandle(handle)
        }
    }

    actual fun delete(path: String): Boolean {
        return DeleteFileW(path) != 0
    }
}

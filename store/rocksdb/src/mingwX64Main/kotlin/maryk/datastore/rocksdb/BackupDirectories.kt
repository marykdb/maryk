@file:OptIn(ExperimentalForeignApi::class)

package maryk.datastore.rocksdb

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.FILE_ATTRIBUTE_DIRECTORY
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetFileAttributesW
import platform.windows.OPEN_ALWAYS
import platform.windows.RemoveDirectoryW
import platform.windows.CloseHandle
import platform.windows.CreateDirectoryW
import platform.windows.CreateFileW
import platform.windows.FILE_FLAG_BACKUP_SEMANTICS
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GetFinalPathNameByHandleW
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_EXISTING
import platform.windows.WCHARVar

internal actual fun canonicalBackupDirectory(path: String): String = memScoped {
    require(path.isNotBlank() && '\u0000' !in path) { "Invalid backup directory" }
    val attributes = GetFileAttributesW(path)
    require(attributes != UInt.MAX_VALUE && (attributes and FILE_ATTRIBUTE_DIRECTORY.toUInt()) != 0u) { "Not a directory: $path" }
    val handle = CreateFileW(path, 0u, (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).toUInt(), null,
        OPEN_EXISTING.toUInt(), FILE_FLAG_BACKUP_SEMANTICS.toUInt(), null)
    require(handle != null && handle != INVALID_HANDLE_VALUE) { "Directory must already exist: $path" }
    try {
        val length = GetFinalPathNameByHandleW(handle, null, 0u, 0u)
        require(length > 0u) { "Cannot resolve directory: $path" }
        val buffer = allocArray<WCHARVar>(length.toInt() + 1)
        require(GetFinalPathNameByHandleW(handle, buffer, length + 1u, 0u) in 1u..length) { "Cannot resolve directory: $path" }
        val canonical = buffer.toKString().replace('\\', '/')
        if (canonical.startsWith("//?/UNC/", ignoreCase = true)) "//" + canonical.substring(8)
        else canonical.removePrefix("//?/")
    } finally {
        CloseHandle(handle)
    }
}

internal actual fun createNewBackupDirectory(path: String) {
    require(CreateDirectoryW(path, null) != 0) { "Restore destination must not exist and its parent must be writable: $path" }
}

internal actual fun removeBackupDirectory(path: String) {
    check(RemoveDirectoryW(path) != 0) { "Cannot remove restore completion guard: $path" }
}

internal actual fun backupPathExists(path: String): Boolean = GetFileAttributesW(path) != UInt.MAX_VALUE

internal actual fun lockBackupRepository(directory: String): AutoCloseable {
    val handle = CreateFileW("$directory/.maryk-backup.lock", (GENERIC_READ or GENERIC_WRITE.toUInt()),
        0u, null, OPEN_ALWAYS.toUInt(), FILE_ATTRIBUTE_NORMAL.toUInt(), null)
    check(handle != null && handle != INVALID_HANDLE_VALUE) { "Backup repository already has an owner or is not writable: $directory" }
    return object : AutoCloseable {
        override fun close() { CloseHandle(handle) }
    }
}

internal actual fun backupDirectorySyncRequired(): Boolean = false

// CreateFileW's exclusive sharing mode excludes aliases at the kernel level and
// does not have POSIX's close-another-descriptor lock-release behavior.
internal actual fun backupOwnershipFileKey(directory: String): String? = directory.lowercase()

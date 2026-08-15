package maryk

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.rmdir
import platform.posix.unlink
import platform.windows.DeleteFileW
import platform.windows.FILE_ATTRIBUTE_DIRECTORY
import platform.windows.FILE_ATTRIBUTE_REPARSE_POINT
import platform.windows.GetFileAttributesW
import platform.windows.RemoveDirectoryW

private const val invalidFileAttributes = UInt.MAX_VALUE

private fun isSymbolicLink(path: String): Boolean {
    val attributes = GetFileAttributesW(path)
    return attributes != invalidFileAttributes && (attributes and FILE_ATTRIBUTE_REPARSE_POINT.toUInt()) != 0u
}

private fun deleteSymbolicLink(path: String): Boolean {
    val attributes = GetFileAttributesW(path)
    return if ((attributes and FILE_ATTRIBUTE_DIRECTORY.toUInt()) != 0u) {
        RemoveDirectoryW(path) != 0
    } else {
        DeleteFileW(path) != 0
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun deleteFolder(path: String): Boolean {
    if (isSymbolicLink(path)) return deleteSymbolicLink(path)

    val directory = opendir(path) ?: return unlink(path) == 0

    try {
        while (true) {
            val entry = readdir(directory) ?: break
            val name = entry.pointed.d_name.toKString()
            if (name == "." || name == "..") continue

            if (!deleteFolder("$path/$name")) return false
        }
    } finally {
        closedir(directory)
    }

    return rmdir(path) == 0
}

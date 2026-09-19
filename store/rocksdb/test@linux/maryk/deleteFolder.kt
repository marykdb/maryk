package maryk

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.closedir
import platform.posix.lstat
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.rmdir
import platform.posix.stat
import platform.posix.unlink

private const val fileTypeMask = 0xF000
private const val symbolicLink = 0xA000

@OptIn(ExperimentalForeignApi::class)
private fun isSymbolicLink(path: String) = memScoped {
    val status = alloc<stat>()
    lstat(path, status.ptr) == 0 && (status.st_mode.toInt() and fileTypeMask) == symbolicLink
}

@OptIn(ExperimentalForeignApi::class)
actual fun deleteFolder(path: String): Boolean {
    if (isSymbolicLink(path)) return unlink(path) == 0

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

@file:OptIn(ExperimentalForeignApi::class)

package maryk.datastore.rocksdb

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.F_SETLK
import platform.posix.F_WRLCK
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.SEEK_SET
import platform.posix.close
import platform.posix.fcntl
import platform.posix.flock
import platform.posix.lstat
import platform.posix.open
import platform.posix.rmdir
import platform.posix.free
import platform.posix.mkdir
import platform.posix.realpath
import platform.posix.stat

internal actual fun canonicalBackupDirectory(path: String): String = memScoped {
    require(path.isNotBlank() && '\u0000' !in path) { "Invalid backup directory" }
    val resolved = requireNotNull(realpath(path, null)) { "Directory must already exist: $path" }
    val canonical = try { resolved.toKString() } finally { free(resolved) }
    val info = alloc<stat>()
    require(stat(canonical, info.ptr) == 0 && (info.st_mode.toInt() and 0xF000) == 0x4000) {
        "Not a directory: $path"
    }
    canonical
}

internal actual fun createNewBackupDirectory(path: String) {
    require(mkdir(path, 0x1C0u) == 0) { "Restore destination must not exist and its parent must be writable: $path" }
}

internal actual fun removeBackupDirectory(path: String) {
    check(rmdir(path) == 0) { "Cannot remove restore completion guard: $path" }
}

internal actual fun backupPathExists(path: String): Boolean = memScoped {
    val info = alloc<stat>()
    lstat(path, info.ptr) == 0
}

internal actual fun lockBackupRepository(directory: String): AutoCloseable {
    val descriptor = open("$directory/.maryk-backup.lock", O_RDWR or O_CREAT, 0x180)
    check(descriptor >= 0) { "Cannot open backup ownership lock: $directory" }
    val acquired = memScoped {
        val lock = alloc<flock>()
        lock.l_type = F_WRLCK.toShort()
        lock.l_whence = SEEK_SET.toShort()
        lock.l_start = 0
        lock.l_len = 0
        fcntl(descriptor, F_SETLK, lock.ptr) == 0
    }
    if (!acquired) {
        close(descriptor)
        error("Backup repository already has an owner: $directory")
    }
    return object : AutoCloseable {
        override fun close() { close(descriptor) }
    }
}

internal actual fun backupDirectorySyncRequired(): Boolean = true

internal actual fun backupOwnershipFileKey(directory: String): String? = memScoped {
    val info = alloc<stat>()
    if (stat("$directory/.maryk-backup.lock", info.ptr) != 0) null
    else "${info.st_dev}:${info.st_ino}"
}

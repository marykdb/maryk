@file:OptIn(ExperimentalForeignApi::class)

package maryk.datastore.rocksdb

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.stat

internal actual fun posixBackupFileIdentity(path: String): String? = memScoped {
    val info = alloc<stat>()
    if (stat(path, info.ptr) != 0) null else "${info.st_dev}:${info.st_ino}"
}

package maryk.datastore.rocksdb

import java.io.RandomAccessFile
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.ENOENT
import android.system.OsConstants.ENOTDIR
import java.io.File

internal actual fun canonicalBackupDirectory(path: String): String {
    require(path.isNotBlank() && '\u0000' !in path) { "Invalid backup directory" }
    val file = File(path).canonicalFile
    requireJniBackupPath(file.path)
    require(file.isDirectory) { "Directory must already exist: $path" }
    return if (File.separatorChar == '\\') file.path.replace('\\', '/') else file.path
}

internal actual fun createNewBackupDirectory(path: String) {
    requireJniBackupPath(path)
    require(File(path).mkdir()) { "Restore destination must not exist and its parent must be writable: $path" }
}

internal actual fun removeBackupDirectory(path: String) {
    check(File(path).delete()) { "Cannot remove restore completion guard: $path" }
}

internal actual fun backupPathExists(path: String): Boolean = try {
    Os.lstat(path)
    true
} catch (error: ErrnoException) {
    if (error.errno == ENOENT || error.errno == ENOTDIR) false else throw error
}

internal actual fun lockBackupRepository(directory: String): AutoCloseable {
    val file = RandomAccessFile("$directory/.maryk-backup.lock", "rw")
    try {
        val lock = checkNotNull(file.channel.tryLock()) { "Backup repository already has an owner: $directory" }
        return object : AutoCloseable {
            override fun close() {
                try { lock.release() } finally { file.close() }
            }
        }
    } catch (error: Throwable) {
        file.close()
        throw error
    }
}

internal actual fun backupDirectorySyncRequired(): Boolean = File.separatorChar != '\\'

private fun requireJniBackupPath(path: String) {
    require(path.none { it in '\uD800'..'\uDFFF' }) {
        "RocksDB JNI backup paths cannot contain supplementary Unicode characters"
    }
}

internal actual fun backupOwnershipFileKey(directory: String): String? = try {
    val info = Os.stat("$directory/.maryk-backup.lock")
    "${info.st_dev}:${info.st_ino}"
} catch (error: ErrnoException) {
    if (error.errno == ENOENT) null else throw error
}

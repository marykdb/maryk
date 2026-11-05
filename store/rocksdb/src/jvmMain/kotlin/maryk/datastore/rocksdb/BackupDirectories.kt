package maryk.datastore.rocksdb

import java.io.RandomAccessFile
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
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

internal actual fun backupPathExists(path: String): Boolean = Files.exists(File(path).toPath(), NOFOLLOW_LINKS)

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
    if (File.separatorChar == '\\') {
        // OpenJDK's Windows BasicFileAttributes.fileKey() is null. Windows locks
        // are handle-based and do not suffer POSIX's unrelated-close release.
        "windows:${directory.lowercase()}"
    } else {
        val attributes = Files.readAttributes(File(directory, ".maryk-backup.lock").toPath(), BasicFileAttributes::class.java)
        checkNotNull(attributes.fileKey()) { "Backup filesystem does not provide stable file identities" }.toString()
    }
} catch (_: NoSuchFileException) {
    null
}

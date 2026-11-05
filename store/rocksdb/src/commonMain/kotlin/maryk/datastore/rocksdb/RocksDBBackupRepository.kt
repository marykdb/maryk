package maryk.datastore.rocksdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import maryk.core.base64.Base64Maryk
import maryk.datastore.shared.runCatchingNonFatal
import maryk.datastore.rocksdb.metadata.parseStoreMeta
import maryk.file.File
import maryk.rocksdb.BackupEngine
import maryk.rocksdb.BackupEngineOptions
import maryk.rocksdb.BackupInfo
import maryk.rocksdb.RestoreOptions
import maryk.rocksdb.getDefaultEnv
import maryk.rocksdb.loadRocksDBLibrary
import maryk.rocksdb.openBackupEngine

/** Copied information about a complete physical backup, including the settings needed to reopen it. */
data class RocksDBBackupInfo(
    val id: Int,
    val timestampSeconds: Long,
    val sizeBytes: Long,
    val fileCount: Int,
    val keepAllVersions: Boolean,
    val keepUpdateHistoryIndex: Boolean,
)

/** Throughput limits are bytes/second; zero means unlimited. */
data class RocksDBBackupOptions(
    val backupRateLimit: Long = 0,
    val restoreRateLimit: Long = 0,
    val maxBackgroundOperations: Int = 1,
) {
    init {
        require(backupRateLimit >= 0 && restoreRateLimit >= 0) { "Backup rate limits cannot be negative" }
        require(maxBackgroundOperations > 0) { "Backup parallelism must be positive" }
    }
}

/**
 * Incremental physical RocksDB backups. An exclusive filesystem lock protects repository ownership.
 * Operations are serialized and run on IO. Close this repository explicitly with [close].
 * Portable Maryk backup/restore remains the separate, cross-backend record export API.
 */
class RocksDBBackupRepository private constructor(
    val directory: String,
    private val options: BackupEngineOptions,
    private val engine: BackupEngine,
    private val ownership: AutoCloseable,
) {
    private val mutex = Mutex()
    private var closed = false

    private suspend fun <T> access(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Backup repository is closed" }
            block()
        }
    }

    /** Captures the current physical database; it does not select an earlier Maryk HLC version. */
    suspend fun createBackup(store: RocksDBDataStore, flushBeforeBackup: Boolean = true): RocksDBBackupInfo =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(!closed) { "Backup repository is closed" }
                store.withPhysicalBackup { db, storePath, metadata ->
                    requireSeparateBackupDirectories(directory, canonicalBackupDirectory(storePath))
                    val applicationMetadata = encodeBackupMetadata(store.keepAllVersions, store.keepUpdateHistoryIndex, metadata)
                    engine.createNewBackupWithMetadata(db, applicationMetadata, flushBeforeBackup)
                    // Exclusive repository ownership makes the newest successful backup ours.
                    checkNotNull(engine.getBackupInfo().maxByOrNull { it.backupId() }) {
                        "RocksDB did not publish a backup"
                    }.toMarykInfo()
                }
            }
        }

    suspend fun backups(): List<RocksDBBackupInfo> = access {
        engine.getBackupInfo().mapNotNull { it.readableInfo() }.sortedBy { it.id }
    }

    /** Engine-readable backups whose Maryk metadata is invalid or from an unsupported version. */
    suspend fun unreadableBackupIds(): List<Int> = access {
        engine.getBackupInfo().filter { it.readableInfo() == null }.map { it.backupId() }.sorted()
    }

    /** Structural corruption reported by the engine; an empty result is not a full checksum verification. */
    suspend fun corruptedBackupIds(): List<Int> = access { engine.getCorruptedBackups().toList() }

    /** Retention is explicit and only runs when requested. */
    suspend fun purgeOldBackups(keep: Int) = access {
        require(keep > 0) { "Retain at least one backup" }
        // Unsupported/corrupted Maryk metadata must not displace usable recovery points.
        val inventory = engine.getBackupInfo()
        val usable = inventory.mapNotNull { it.readableInfo() }.sortedBy { it.id }
        if (usable.size == inventory.size) engine.purgeOldBackups(keep)
        else usable.dropLast(keep).forEach { engine.deleteBackup(it.id) }
    }

    suspend fun deleteBackup(id: Int) = access {
        // RocksDB already collects unreferenced files and retries cleanup when needed.
        engine.deleteBackup(id)
    }

    /**
     * Restores into an absent path whose parent exists. Never overwrites an existing directory.
     * RocksDB verifies restored file checksums. A failure may leave a partial destination; do not open it.
     * On success reopen through Maryk with the returned settings and validate before publishing it.
     */
    suspend fun restoreBackup(id: Int, destination: String): RocksDBBackupInfo = access {
        val backup = engine.getBackupInfo().singleOrNull { it.backupId() == id }
            ?: throw IllegalArgumentException("Backup $id is unavailable: missing or structurally corrupt")
        val metadata = decodeBackupMetadata(backup.appMetadata())
        val path = newRestorePath(destination)
        requireSeparateBackupDirectories(directory, path)
        createNewBackupDirectory(path)
        // RocksDB removes ordinary files before restoring. A directory survives its DeleteFile pass.
        val incomplete = "$path/$INCOMPLETE_RESTORE_DIRECTORY"
        createNewBackupDirectory(incomplete)
        check(syncBackupDirectoryEntry(incomplete)) { "Cannot sync restore guard" }
        val restoreOptions = RestoreOptions(false)
        try {
            engine.restoreDbFromBackup(id, path, path, restoreOptions)
            // This payload is part of the same engine backup, not a separately copied live sidecar.
            File.writeText("$path/MARYK_META.yml", metadata.storeMeta)
            check(File.syncFile("$path/MARYK_META.yml")) { "Cannot sync restored Maryk metadata" }
            finishPhysicalRestore(path)
        } finally {
            restoreOptions.close()
        }
        backup.toMarykInfo()
    }

    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            if (!closed) {
                closed = true
                try {
                    engine.close()
                } finally {
                    try { options.close() } finally { ownership.close() }
                }
            }
        }
    }

    companion object {
        /** [directory] must already exist and must be dedicated to this repository. */
        suspend fun open(directory: String, options: RocksDBBackupOptions = RocksDBBackupOptions()): RocksDBBackupRepository {
            var opened: RocksDBBackupRepository? = null
            try {
                return withContext(Dispatchers.IO) {
                    loadRocksDBLibrary()
                    val path = canonicalBackupDirectory(directory)
                    val ownership = acquireRepositoryOwnership(path)
                    var nativeOptions: BackupEngineOptions? = null
                    var engine: BackupEngine? = null
                    try {
                        nativeOptions = BackupEngineOptions(path)
                        // Retain RocksDB 11.8's default session-ID naming. The Native boolean
                        // checksum-sharing setter selects legacy CRC naming and rereads old SSTs.
                        nativeOptions.setShareTableFiles(true)
                            .setSync(true).setDestroyOldData(false).setBackupLogFiles(true)
                            .setBackupRateLimit(options.backupRateLimit).setRestoreRateLimit(options.restoreRateLimit)
                            .setMaxBackgroundOperations(options.maxBackgroundOperations)
                        check(nativeOptions.shareFilesWithChecksum()) { "Safe shared-file identities must be enabled" }
                        engine = openBackupEngine(getDefaultEnv(), nativeOptions)
                        RocksDBBackupRepository(path, nativeOptions, engine, ownership).also { opened = it }
                    } catch (error: Throwable) {
                        try { engine?.close() } finally {
                            try { nativeOptions?.close() } finally { ownership.close() }
                        }
                        throw error
                    }
                }
            } catch (error: Throwable) {
                // withContext may cancel at the dispatcher boundary after native allocation succeeded.
                opened?.close()
                throw error
            }
        }
    }
}

private const val BACKUP_METADATA_HEADER = "maryk-rocksdb-backup:2"
private const val LEGACY_BACKUP_METADATA_HEADER = "maryk-rocksdb-backup:1"
// RocksDB BackupEngine rejects application metadata larger than 1 MiB, including our header.
private const val MAX_BACKUP_META_BYTES = 1024 * 1024
internal const val INCOMPLETE_RESTORE_DIRECTORY = ".maryk-restore-incomplete"

internal data class PhysicalBackupMetadata(val history: Boolean, val updateHistory: Boolean, val storeMeta: String)

internal fun encodeBackupMetadata(history: Boolean, updateHistory: Boolean, storeMeta: String): String {
    // JNI uses modified UTF-8 for jstring, while Native uses UTF-8. Keep the C API
    // string ASCII so supplementary characters and control escapes survive both.
    val payload = Base64Maryk.encode(storeMeta.encodeToByteArray())
    val encoded = "$BACKUP_METADATA_HEADER\n$history\n$updateHistory\n$payload"
    require(encoded.encodeToByteArray().size <= MAX_BACKUP_META_BYTES) { "Physical backup metadata exceeds RocksDB's 1 MiB limit" }
    parseStoreMeta(storeMeta)
    return encoded
}

internal fun decodeBackupMetadata(value: String?): PhysicalBackupMetadata {
    require(value != null && value.length <= MAX_BACKUP_META_BYTES && value.encodeToByteArray().size <= MAX_BACKUP_META_BYTES) { "Missing or oversized Maryk backup metadata" }
    val parts = value.split('\n', limit = 4)
    require(parts.size == 4 && parts[0] in setOf(BACKUP_METADATA_HEADER, LEGACY_BACKUP_METADATA_HEADER)) { "Unsupported Maryk physical backup format" }
    val history = requireNotNull(parts[1].toBooleanStrictOrNull()) { "Invalid backup history flag" }
    val updateHistory = requireNotNull(parts[2].toBooleanStrictOrNull()) { "Invalid backup update-history flag" }
    val storeMeta = if (parts[0] == BACKUP_METADATA_HEADER) {
        Base64Maryk.decode(parts[3]).decodeToString(throwOnInvalidSequence = true)
    } else parts[3]
    parseStoreMeta(storeMeta)
    return PhysicalBackupMetadata(history, updateHistory, storeMeta)
}

private fun BackupInfo.toMarykInfo(): RocksDBBackupInfo {
    val metadata = decodeBackupMetadata(appMetadata())
    return RocksDBBackupInfo(backupId(), timestamp(), size(), numberFiles(), metadata.history, metadata.updateHistory)
}

private fun BackupInfo.readableInfo(): RocksDBBackupInfo? = runCatchingNonFatal { toMarykInfo() }.getOrNull()

internal fun requireSeparateBackupDirectories(first: String, second: String) {
    val a = first.trimEnd('/')
    val b = second.trimEnd('/')
    val ignoreCase = a.getOrNull(1) == ':' || a.startsWith("//")
    require(!a.equals(b, ignoreCase) && !a.startsWith("$b/", ignoreCase) && !b.startsWith("$a/", ignoreCase)) {
        "Backup repository and database directories must be separate and cannot contain one another"
    }
}

private fun newRestorePath(path: String): String {
    require(path.isNotBlank() && '\u0000' !in path) { "Invalid restore destination" }
    val currentDirectory = canonicalBackupDirectory(".")
    val windows = currentDirectory.getOrNull(1) == ':' || currentDirectory.startsWith("//")
    val normalized = (if (windows) path.replace('\\', '/') else path).trimEnd('/')
    val name = normalized.substringAfterLast('/')
    require(name.isNotEmpty() && name != "." && name != "..") { "Restore destination must name a new directory" }
    val parent = normalized.substringBeforeLast('/', ".").ifEmpty { "/" }
    return canonicalBackupDirectory(parent).trimEnd('/') + "/" + name
}

internal expect fun canonicalBackupDirectory(path: String): String
internal expect fun createNewBackupDirectory(path: String)

internal expect fun removeBackupDirectory(path: String)
internal expect fun backupPathExists(path: String): Boolean
internal expect fun lockBackupRepository(directory: String): AutoCloseable

// Closing any descriptor can release a POSIX process's lock. Serialize acquisition
// and check the lock file's identity before opening it, including hard-link aliases.
private val repositoryAcquisitionMutex = Mutex()
private val ownedRepositoryIdentities = atomic(setOf<String>())

internal expect fun backupOwnershipFileKey(directory: String): String?

private suspend fun acquireRepositoryOwnership(directory: String): AutoCloseable = repositoryAcquisitionMutex.withLock {
    val normalized = if (directory.getOrNull(1) == ':' || directory.startsWith("//")) directory.lowercase() else directory
    val pathKey = "path:$normalized"
    val existingFileKey = backupOwnershipFileKey(directory)?.let { "file:$it" }
    val current = ownedRepositoryIdentities.value
    check(pathKey !in current && (existingFileKey == null || existingFileKey !in current)) {
        "Backup repository already has an owner: $directory"
    }
    val lock = lockBackupRepository(directory)
    try {
        val fileKey = checkNotNull(backupOwnershipFileKey(directory)) { "Cannot identify backup ownership file" }
        val keys = setOf(pathKey, "file:$fileKey")
        while (true) {
            val before = ownedRepositoryIdentities.value
            check(keys.none { it in before }) { "Backup ownership file changed during acquisition" }
            if (ownedRepositoryIdentities.compareAndSet(before, before + keys)) break
        }
        val closed = atomic(false)
        object : AutoCloseable {
            override fun close() {
                if (!closed.getAndSet(true)) {
                    try { lock.close() } finally {
                        while (true) {
                            val before = ownedRepositoryIdentities.value
                            if (ownedRepositoryIdentities.compareAndSet(before, before - keys)) break
                        }
                    }
                }
            }
        }
    } catch (error: Throwable) {
        lock.close()
        throw error
    }
}

internal expect fun backupDirectorySyncRequired(): Boolean

private fun syncBackupDirectoryEntry(path: String): Boolean =
    File.syncParentDirectory(path) || !backupDirectorySyncRequired()

/** Keep the guard until durable metadata publication; the callback permits directory-sync fault tests. */
internal fun finishPhysicalRestore(path: String, syncEntry: (String) -> Boolean = ::syncBackupDirectoryEntry) {
    check(syncEntry("$path/MARYK_META.yml")) { "Cannot sync restored database metadata directory" }
    check(syncEntry(path)) { "Cannot sync restored database parent" }
    val incomplete = "$path/$INCOMPLETE_RESTORE_DIRECTORY"
    removeBackupDirectory(incomplete)
    try {
        check(syncEntry(incomplete)) { "Cannot sync restore completion" }
    } catch (error: Exception) {
        // A failed completion must not leave an apparently publishable directory.
        runCatchingNonFatal {
            createNewBackupDirectory(incomplete)
            syncEntry(incomplete)
        }.exceptionOrNull()?.let(error::addSuppressed)
        throw error
    }
}

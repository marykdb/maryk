# Native RocksDB backups

Use `RocksDBBackupRepository` to recover a complete RocksDB store, including its
indexes, Maryk metadata and whatever history it retains. It works with or without
`keepAllVersions`. For backend transfer, choose a [portable backup](../../shared/docs/backup-restore.md).

## Before starting

- Create a writable repository directory outside the live database tree.
- Finish schema and index migrations before backing up.
- Use paths without supplementary Unicode characters, such as emoji, on JVM and
  Android. Those paths are rejected because of RocksDB JNI path encoding.
- Use ASCII-only repository paths on Windows Native (MinGW), including parent
  directories. The pinned `rocksdb-multiplatform` 11.8.1 binding checks writability
  with a narrow Windows API and can reject writable Unicode paths. Maryk's
  Unicode directory and lock handling does not remove this engine limitation.
- Keep the original encryption keys separately; they are not included in a backup.

An open repository owns an exclusive filesystem lock. Close it after use, and do
not delete `.maryk-backup.lock` or modify repository files yourself. A leftover
lock file after a crash is harmless; the operating system releases the lock.

## Create and retain backups

The example assumes `store` is open and `/data/backups/accounts` already exists.

```kotlin
import maryk.datastore.rocksdb.RocksDBBackupRepository

val backups = RocksDBBackupRepository.open("/data/backups/accounts")
try {
    val backup = backups.createBackup(store)
    println("Created backup ${backup.id}")
    // Retain recovery points only after the new backup succeeds.
    backups.purgeOldBackups(keep = 7)
} finally {
    backups.close()
}
```

Ordinary writes may continue during capture. The default flush includes writes
made with WAL disabled. Backups are durably synced; retention runs only when
requested. Repeated backups share unchanged SST files, so the reported backup
size is its logical size, not newly allocated disk space.

Use `store.close()` and repository `close()` for cleanup; they wait for active
backups. The lower-level `closeResources()` refuses an active backup. Native
backup calls are blocking and do not promise immediate coroutine cancellation.

## Restore into a new directory

Choose `recoveryBackupId` from `backups.backups()` for the recovery point you need.
The destination must not exist, even as an empty directory; its parent must exist. The repository and destination must not contain
one another. Use compatible Maryk/RocksDB versions, model registrations and codecs.

```kotlin
import maryk.datastore.rocksdb.RocksDBDataStore

val backups = RocksDBBackupRepository.open("/data/backups/accounts")
try {
    val restored = backups.restoreBackup(
        id = recoveryBackupId,
        destination = "/data/recovery/accounts-checked",
    )
    val recoveredStore = RocksDBDataStore.open(
        relativePath = "/data/recovery/accounts-checked",
        dataModelsById = accountModels,
        keepAllVersions = restored.keepAllVersions,
        keepUpdateHistoryIndex = restored.keepUpdateHistoryIndex,
        // Supply the original fieldEncryptionProvider if the data uses it.
    )
    try {
        recoveredStore.verifyStorageChecksums()
        // Check known records, indexed queries and application invariants.
    } finally {
        recoveredStore.close()
    }
} finally {
    backups.close()
}
```

`accountModels` is the original application's model map. Restore checks file
checksums and the embedded Maryk metadata, then restores the matching
`MARYK_META.yml`. It does not switch the application or migrate its schema.
Validate the recovered store before changing application configuration.

A failed restore can leave a `.maryk-restore-incomplete` directory. Maryk refuses
to open that destination. Discard it and retry into another absent path; do not
remove the guard to bypass a failed restore.

## Check recovery and keep an off-device copy

`backups()` lists backups with supported, readable Maryk metadata.
`unreadableBackupIds()` lists other engine-readable backups, and
`corruptedBackupIds()` reports structural corruption known to RocksDB. One bad
backup does not block healthy recovery. Empty error lists are not a full
checksum verification: rehearse an actual restore and run `verifyStorageChecksums()`.

Use `deleteBackup(id)` or `purgeOldBackups(keep)` for retention, rather than deleting
shared files. If some metadata is unreadable, retention keeps the requested number
of readable recovery points and leaves unreadable backups untouched.

Copy the complete repository off-device while its owner is closed or quiescent.
Keep shared files and metadata together. The API needs a writable local directory;
copy read-only recovery media to a writable working directory first. A same-disk
backup protects against application mistakes, but not disk loss. Protect stored
bytes as production data, including any retained historical plaintext.

## Advanced options and compatibility

`RocksDBBackupOptions` controls throughput and background work; tune it against
foreground traffic and disk capacity. `flushBeforeBackup = false` may avoid a
flush, but is safe only when every relevant write uses the WAL. A checkpoint can
be useful for a fast local clone, but shared storage does not protect against
disk loss. See the [upstream backup guide](https://github.com/facebook/rocksdb/wiki/How-to-backup-RocksDB)
for engine-level options.

The embedded Maryk metadata must fit the engine's 1 MiB application-metadata limit;
oversized or invalid metadata is rejected before capture. Native restore requires
compatible custom comparators and codecs and does not promise downgrade
compatibility. Windows currently has weaker directory-entry durability because
the file abstraction does not expose directory syncing there.

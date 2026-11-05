# RocksDB backups and portable backups

Maryk offers two complementary backup paths. Choose according to what you need
to recover and where the result must be restored.

| | Native RocksDB backup | Portable Maryk backup |
| --- | --- | --- |
| API | `RocksDBBackupRepository` in `store/rocksdb` | Shared `backup(writer)` / `restore(reader)` |
| Contents | Physical engine files plus Maryk store metadata | Versioned application records described by a manifest |
| Destination | A compatible RocksDB store | Compatible Maryk datastore implementations |
| History required | No; preserves whatever history exists | `keepAllVersions` required on source and target |
| Recovery point | Consistent current engine state selected during backup | Selected authoritative Maryk snapshot version |
| Indexes and engine metadata | Preserved, including receipts, tombstones and allocation state | Reconstructed through versioned replay; not an engine clone |
| Repeated backups | Immutable SST files shared between backups | Logical export through the writer contract |
| Typical purpose | Operational recovery of an embedded RocksDB store | Portable export, backend transfer and logical recovery |

The formats and APIs are separate. A native backup cannot be passed to the
portable reader, and a portable archive cannot be opened by RocksDB's backup
engine. Existing portable backup behavior and format remain unchanged. See the
[portable backup and restore guide](../../shared/docs/backup-restore.md) for its
history, hard-deletion, replay-limit and publication requirements.

## Create and retain native backups

Create a dedicated repository directory first, outside the live database tree.
Its parent and the directory itself must already exist and be writable. Opening acquires an
exclusive filesystem ownership lock; another Maryk repository owner, including
one in another process, is refused. Canonical aliases share that ownership. Local registration also checks lock-file
identity, so a failed alias acquisition cannot release another local owner's
process-scoped POSIX lock.
The persistent `.maryk-backup.lock` file is harmless after close or a process
crash: the operating system releases its lock when the owner exits. Do not delete
or replace that file, or independently modify repository files while it is open.
Use application-controlled repository and restore-parent directories. External
tools must also respect exclusive ownership; raw RocksDB backup engines
do not participate in Maryk's ownership protocol.

```kotlin
import maryk.datastore.rocksdb.RocksDBBackupOptions
import maryk.datastore.rocksdb.RocksDBBackupRepository

val backups = RocksDBBackupRepository.open(
    directory = "/data/backups/accounts",
    options = RocksDBBackupOptions(
        backupRateLimit = 32L * 1024 * 1024, // bytes/second; zero is unlimited
        maxBackgroundOperations = 2,
    ),
)
try {
    val backup = backups.createBackup(store)
    println("Created backup ${backup.id}: ${backup.sizeBytes} logical bytes")
    val retained = backups.backups()
    // Only apply retention after the new backup has succeeded.
    backups.purgeOldBackups(keep = 7)
} finally {
    backups.close()
}
```

The repository uses RocksDB `BackupEngine` with its modern session-ID SST naming
and safe shared-file identities. This avoids the legacy mode's repeated checksum
reads of unchanged modern SSTs; older SSTs and blob files can still use the
engine's legacy fallback. Restored data is checksum-verified in either mode. A backup's reported size is its logical size, not newly allocated
repository bytes: retained backups may reference the same SSTs. Backups are
synced, old backups are preserved on open, and retention runs only when requested.
`deleteBackup(id)` and `purgeOldBackups(keep)` use the engine to delete references
and let the engine reclaim unused files. Healthy repositories use RocksDB's
batched retention operation. If some backups have unreadable Maryk metadata,
retention keeps the requested number of metadata-readable backups and leaves unreadable
ones untouched. Do not delete shared files yourself.

`createBackup` flushes before copying by default. This includes writes made with
WAL disabled. `flushBeforeBackup = false` instead allows the engine to include
live WAL files, avoiding that explicit flush; use this mode only when all relevant
writes use the WAL. Neither mode selects an earlier Maryk HLC snapshot.

Ordinary writes may continue while the engine captures a consistent physical
state across column families. Subsequent hard deletion cannot erase the files
already retained by that backup. The operation requires an initialized store
without pending schema or index migrations. Complete migrations first. Maryk
holds its store-metadata lock while capturing `MARYK_META.yml` and creating the
engine backup. The metadata and history settings are embedded in that backup's
application metadata, so they remain tied to its backup ID and do not depend on
later changes to the live sidecar. The complete encoded payload must fit RocksDB's
1 MiB application-metadata limit; oversized or structurally invalid metadata is
rejected before a backup is started. Physical backup metadata version 2 uses an
ASCII envelope with Base64-encoded UTF-8 YAML, avoiding JNI/Native string-encoding
differences for Unicode model names. The reader also accepts the original version
1 envelope. These version numbers are independent of the portable backup format.

Backup operations run on the IO dispatcher. `store.close()` waits for an active
backup before releasing native handles. Calling the lower-level synchronous
`closeResources()` during a backup fails; use `close()` for normal lifecycle
management. Repository `close()` likewise waits for its active operation. Native
backup calls are blocking and do not promise immediate coroutine cancellation.

## Restore and validate before switching

Restore requires an absent destination directory with an existing parent.
Existing directories, including empty ones, are refused. Repository and database
paths must not contain one another; canonical paths resolve directory aliases
before checking containment.

```kotlin
val backups = RocksDBBackupRepository.open("/data/backups/accounts")
try {
    val selected = backups.backups().last()
    val restored = backups.restoreBackup(
        id = selected.id,
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
        // Check application invariants before switching the application to this path.
    } finally {
        recoveredStore.close()
    }
} finally {
    backups.close()
}
```

RocksDB checks file checksums while restoring. Maryk validates the physical
backup metadata format before creating the destination and restores its matching
`MARYK_META.yml`. That file is synced; directory-sync failures fail the restore
on supported platforms. Windows does not expose directory syncing through the
current file abstraction, so it has a weaker directory-entry durability guarantee.
`corruptedBackupIds()` reports structural corruption known to the engine.
`backups()` lists backups with readable, supported Maryk metadata (including known
metadata and index-format versions);
`unreadableBackupIds()` identifies other engine-readable backups. One unreadable
backup does not prevent restoring healthy backups. An empty list does not establish that every stored byte has been
read and checksum-verified. The current common binding has no standalone
`BackupEngine.verifyBackup` API; test recovery by restoring to a disposable path,
opening through Maryk and calling `verifyStorageChecksums()`.

A failed restore may leave a partial destination with a
`.maryk-restore-incomplete` directory. Maryk refuses to open a store with that
completion guard; it is removed only after files and metadata are restored and
synced. Do not remove the guard to bypass a failed restore. Discard the partial
destination explicitly and retry into another absent path. Restore never switches the active
application store or replaces the live directory. Retention policy should allow
recovery drills before older known-good backups are removed.

## Performance choices

Incremental `BackupEngine` is the default for independent recovery copies. A
checkpoint can be faster for a local clone because it can hard-link SSTs, but
it shares the underlying storage and does not protect against disk loss.

The repository retains one engine per open owner, uses the native batched
retention path when possible, and avoids redundant full garbage-collection and
backup-inventory scans. First-backup time is dominated by copying existing data;
repeat-backup time depends on changed SSTs, WAL volume and durable metadata writes.
Tune `maxBackgroundOperations` and throughput limits against the actual disk and
foreground workload. There is no universal fastest thread count.

The default flush includes WAL-disabled writes. WAL-inclusive mode may avoid
flush latency when every write is WAL-protected. Durable syncing stays enabled;
this API does not trade recoverability for benchmark throughput. See the
[RocksDB backup guide](https://github.com/facebook/rocksdb/wiki/How-to-backup-RocksDB)
and [11.8.1 backup options](https://github.com/facebook/rocksdb/blob/v11.8.1/include/rocksdb/utilities/backup_engine.h)
for the underlying behavior.

## Compatibility and operational scope

JVM and Android backup filesystem paths currently reject supplementary Unicode
characters (such as emoji): RocksDB JNI marshals paths through modified UTF-8 and
could otherwise select a different filesystem location. Use a path without those
characters. Unicode model names are supported by the ASCII metadata envelope.

Native restore needs a compatible Maryk/RocksDB runtime, model registrations,
custom comparators and codecs. It is not a guarantee of downgrade compatibility
or a substitute for application schema migration. Restoring to another engine
requires the portable backup path instead. It can be useful to keep frequent
native backups for recovery and occasional portable backups for portability.

Field-encryption keys and other application secrets are not bundled. Keep the
required keys available separately. Native backups retain stored bytes, including
any historical plaintext, so protect the repository as production data.

The repository operates on a writable local filesystem directory. To recover
from read-only media, first copy the complete repository to a writable local
working directory; the published binding does not expose a read-only backup
engine. Scheduling, off-device
copying, encryption of the backup repository, and application switching belong to
the caller. Copy the repository only while its owner is closed or otherwise
quiescent; its shared-file inventory and metadata must remain together. A backup
on the same disk provides recovery from application mistakes, but not disk loss.

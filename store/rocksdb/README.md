# Maryk RocksDB Store

An embedded, high‑performance Maryk data store built on [RocksDB](https://rocksdb.org).

Each DataModel is mapped to multiple column families (keys/table/index/unique and historic variants when enabled). See the detailed [Architecture](documentation/architecture.md) and [Storage Layout](documentation/storage.md).
For migration runtime and operations, see [Migrations](documentation/migrations.md).

## Getting Started

Use the following snippet:
```kotlin
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    RocksDBDataStore.open(
        keepAllVersions = false,
        keepUpdateHistoryIndex = false,
        relativePath = "path/to/folder/on/disk/for/store",
        dataModelsById = mapOf(
            1u to Account,
            2u to Course
        )
    ).use {
        // Run operations on the store

        execute(
            Account.add(
                Account(
                    username="test1",
                    password="test1234"
                ),
                Account(
                    username="test2",
                    password="test1234"
                )
            )
        )
    )
}
```

The `.use { ... }` scope closes the store automatically. If you don’t use `use`, call `close()` when finished to free native resources.

Committed atomic requests and transactions persist their version watermark with
their data and receipt, so snapshots remain current after reopening. The first
open after upgrading repairs older watermarks by scanning current rows and
replication tombstones once per model. This uses constant scan memory, but startup
time depends on stored data size. A completion marker skips the repair on later
opens.

Need remote access to this local RocksDB store? Expose it with the
[Remote Store](../remote/README.md) via CLI `serve`.

## Migrations and Version Updates

On open, the store compares stored model definitions with configured models and either applies compatible changes automatically or requires a migration for incompatible schema changes.

For the full migration model, hook contracts, runtime phases, control APIs, lease behavior, and operational guidance, see [Migrations](documentation/migrations.md).

Group migration settings under `migrationConfiguration = MigrationConfiguration(...)`.
Put `migrationHandler`, `migrationExpandHandler`, `migrationVerifyHandler`, and `migrationContractHandler` inside that configuration.

```kotlin
RocksDBDataStore.open(
    // True if the data store should keep all past versions of the data
    keepAllVersions = true,
    keepUpdateHistoryIndex = true,
    relativePath = "path/to/folder/on/disk/for/store", 
    dataModelsById = mapOf(
        1u to Account,
        2u to Course
    ),
    migrationConfiguration = MigrationConfiguration(
        migrationHandler = { context ->
            val rocksDBDataStore = context.store
            val storedDataModel = context.storedDataModel
            val newDataModel = context.newDataModel
            // example
            when (newDataModel) {
                is Account -> when (storedDataModel.version.major) {
                    1.toUShort() -> {
                        // Execute actions on rocksDBDataStore
                        MigrationOutcome.Success
                    }
                    else -> MigrationOutcome.Fatal("Unsupported source version")
                }
                else -> MigrationOutcome.Fatal("Unsupported model")
            }
        }
    ),
    versionUpdateHandler = { rocksDBDataStore, storedDataModel, newDataModel ->
        // example 
        when (storedDataModel) {
            null -> Unit // Do something when model did not exist before
            else -> Unit
        }
    }
)
```

### Lease Behavior

RocksDB default lease is process-local (`RocksDBLocalMigrationLease`).
- Prevents duplicate migration runners inside one process.
- Cross-process migration lease is usually unnecessary for RocksDB because DB lock allows one opener.
- You can still inject custom `migrationLease` for custom orchestration.

## Platform Support

This module is Kotlin Multiplatform and works on JVM, iOS, macOS, tvOS, watchOS, Android, Android Native, Windows and Linux via the `rocksdb-multiplatform` bindings.

For a deeper dive into how data is laid out and how queries execute, check the [Architecture](documentation/architecture.md) and [Storage Layout](documentation/storage.md) docs.

## Options

- `keepAllVersions`: Keep historic table/index/unique column families for time-travel and change-history reads.
- `keepUpdateHistoryIndex`: Add an `UpdateHistory` column family keyed by change version + key for historical paging. `ScanUpdates` may select records through this index, but its emitted synchronization events remain chronological.

## Update history

For finite, newest-first revision pages, use `getUpdateHistory(key, ...)` or
`scanUpdateHistory(...)` with both history options enabled and a ready history
index. Continue with `UpdatesResponse.nextCursor`; the limit counts complete
record-version entries, independently of property-history `maxVersions`.
Both queries scan the existing model-wide history index. See
[history paging](../../core/docs/query.md#getscan-update-history) for filters,
projections, and hard-delete retention limits.

## Sensitive Properties

`RocksDBDataStore.open` accepts a `fieldEncryptionProvider` argument using the shared encryption contract
`maryk.datastore.shared.encryption.FieldEncryptionProvider`.

Use `sensitive = true` on property definitions to encrypt value payloads at rest.

```kotlin
val secret by string(index = 3u, sensitive = true)

val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()
val encryptionProvider = AesGcmHmacSha256EncryptionProvider(
    encryptionKey = keyMaterial.encryptionKey,
    tokenKey = keyMaterial.tokenKey
)
```

Notes:
- Sensitive values are encrypted in table payloads (latest + historic).
- Reads auto-decrypt based on an encrypted payload marker.
- Supported for simple value properties.
- Sensitive+`unique` is supported when `fieldEncryptionProvider` also implements
  `maryk.datastore.shared.encryption.SensitiveIndexTokenProvider`.
- Sensitive properties cannot contribute to primary keys or secondary indexes,
  including composite and transformed indexes. Use a separate explicit blind-index
  field when search is required. Custom indexes without a dependency contract are
  rejected when the model contains sensitive properties.
- Earlier versions could accept unsafe composite indexes. This validation does not
  erase existing plaintext key/index bytes or physical backups. Recover affected
  data into a fresh store with safe definitions, then securely retire old copies.
- Pass `fieldEncryptionProvider = encryptionProvider` to `RocksDBDataStore.open(...)`.

### Hard deletion and history

A hard delete permanently removes a record's current and historic values, including when `keepAllVersions` is enabled. An earlier `toVersion` query cannot recover erased values. Use soft deletion when historical recovery is required.


## Serializable transaction scopes

RocksDB replays through a native optimistic transaction. Point reads and iterators include its pending writes; a revision key participates in conflict detection and changes in each normal write commit. Preview closes without commit, and durable receipts survive reopen.

The [shared contract](../../core/docs/query.md#serializable-transactions) describes
read-your-own-writes, savepoints, conservative revision conflicts, deadlines and
commit resolution. Query and mutation APIs shipped in 0.12.1 retain their existing
contracts outside an explicit transaction.


SQL reporting, metadata, embedded scalar writes, transactional upsert, bounded
windows, joined mutations, correlated queries and complex map/collection expansion
use the common SQL engine over this store's public requests. See the [SQL guide](../../sql/README.md) for supported
syntax, execution strategies, transaction tooling and resource limits. The shared
SQL conformance contract verifies these features through this adapter.

## Opening storage format 2

Opening an older store performs one resumable, exclusive upgrade before
application schema migrations or requests. There is one migration: the released,
unversioned layout (legacy format 1) upgrades directly to storage format 2.
Float/Double values and index components now share the ordered numeric encoding.
The upgrade converts affected current/historical fields, collection qualifiers,
packed value objects and unique lookups, including contextual encryption and
sensitive unique tokens. Original versions, record IDs and references are kept.
Unchanged cells and fixed-width non-floating indexes are not rewritten.
Legacy variable-width index entries gain escaped boundaries and UTF-16 string
ordering. Primary keys whose bytes change require an explicit key/reference
migration and fail preflight.

The store metadata records `maryk.storage/format = 2` only after verification.
Later opens read the stamp and skip the conversion and data scans. A durable
checkpoint and temporary staged rows make interruption recoverable by reopening
with the same stored source schemas. Sensitive unique token translations are
also temporary and are deleted before publication. Complete pending application
schema migrations with their original library before starting this upgrade.
Legacy field encryption is not converted. Do not run old writers during the
upgrade or downgrade afterward. Restart scans and replication subscriptions from
a fresh snapshot; old scan cursors are rejected.

Per-model `maryk.storage/maintenance` metadata separately records completion of
clock repair and optional history/tombstone backfills. Later opens read these
completion flags and the operational clock watermark, skipping old repair-marker
checks and scans. Models first opened later and newly enabled history still get
their required maintenance. This is engine metadata, independent of model schema
versions, with no extra fields on application records.

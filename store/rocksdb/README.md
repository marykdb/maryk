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

## Migrations and version updates

Opening compares stored and configured model definitions. Compatible changes are
handled automatically; incompatible changes require migration hooks. Group these
under `migrationConfiguration = MigrationConfiguration(...)`. Use
`versionUpdateHandler` for post-migration work.

See [Migrations](documentation/migrations.md) for examples, hook ordering, resumable phases,
lease configuration and operational controls.

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

See [store behavior](../shared/docs/store-behavior.md) for undelete, unique
ownership, replication ordering and scans that include deleted records.

## Serializable transaction scopes

RocksDB replays through a native optimistic transaction. Point reads and iterators include its pending writes; a revision key participates in conflict detection and changes in each normal write commit. Preview closes without commit, and durable receipts survive reopen.

The [shared contract](../../core/docs/query.md#serializable-transactions) describes
read-your-own-writes, savepoints, conservative revision conflicts, deadlines and
commit resolution. Query and mutation APIs shipped in 0.12.1 retain their existing
contracts outside an explicit transaction.


For optional SQL queries, add the SQL module and follow the [SQL guide](../../sql/README.md).

## Upgrading existing stores

Opening a released store can run a resumable storage-format conversion before
application schema migrations. Stop old writers, retain a recoverable source copy,
and read the [storage upgrade guide](../shared/docs/storage-format-upgrades.md)
before rollout. Some legacy collection/allocation states require authoritative
recovery; do not remove upgrade metadata to bypass a failure.

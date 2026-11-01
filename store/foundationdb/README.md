# Maryk FoundationDB Store implementation

A FoundationDB-backed implementation of the Maryk data store. This engine maps Maryk's data model and request APIs onto FoundationDB's ordered key/value space using subspaces (directories), transactions, and efficient range reads.

See also:
- [Storage layout](./docs/storage.md)
- [Architecture overview](./docs/architecture.md)
- [Migrations](./docs/migrations.md)
- [Local testing](./docs/local-testing.md)

## Getting Started

Add the FoundationDB store to your application and open a datastore. At minimum you pass a map of DataModels (by id) and optionally whether to keep all versions.

```kotlin
suspend fun main() {
    val store = FoundationDBDataStore.open(
        keepAllVersions = true,                     // keep historic versions
        keepUpdateHistoryIndex = true,             // keep newest-first update history index
        fdbClusterFilePath = "./fdb.cluster",      // or null to use default
        directoryPath = listOf("maryk", "app"),    // directory root (subspace)
        dataModelsById = mapOf(
            1u to Account,
            2u to Course,
        )
    )

    try {
        // Use Maryk APIs as usual
        store.execute(
            Account.add(
                Account(username = "test1", password = "secret1"),
                Account(username = "test2", password = "secret2"),
            )
        )

        val got = store.execute(Account.get(/* keys… */))
        println(got.values)
    } finally {
        store.close()
    }
}
```

Notes:
- The cluster file can be omitted when the default `~/.fdb` setup is used. Tests use `store/foundationdb/fdb.cluster` via `FDB_CLUSTER_FILE`.
- Always close the store (or wrap in your runtime's lifecycle) to release FDB resources.
- Need remote access to this FoundationDB store? Expose it with the [Remote Store](../remote/README.md) via CLI `serve`.

## Migrations and version updates

Opening compares stored and configured model definitions. Compatible changes are
handled automatically; incompatible changes require migration hooks. Group these
under `migrationConfiguration = MigrationConfiguration(...)`. Use
`versionUpdateHandler` for post-migration work.

See [Migrations](docs/migrations.md) for examples, hook ordering, resumable phases,
lease configuration and operational controls.

## Configuration

- `keepAllVersions`: Mirror latest writes into historic subspaces for time travel and change history.
- `keepUpdateHistoryIndex`: Add a per-model `update_history` subspace keyed by change version + key for historical paging. `ScanUpdates` may select records through this index, but its emitted synchronization events remain chronological.
- `fdbClusterFilePath`: Optional path to an FDB cluster file; uses default environment if null.
- `directoryPath`: Subspace root path under which model directories are created.
- `databaseOptionsSetter`: Lambda executed once during startup on the underlying `DatabaseOptions`. Use this to enable tracing, tweak locality, or set transaction logging limits without forking Maryk.
- `fieldEncryptionProvider`: Optional field-value encryption provider. Required when any model property is marked as sensitive (`sensitive = true`).

Example: set custom transaction retry limits

```kotlin
val store = FoundationDBDataStore.open(
    dataModelsById = mapOf(1u to Account),
    databaseOptionsSetter = {
        setTransactionRetryLimit(3)
        setTransactionMaxRetryDelay(5000)
    }
)
```

### Update history

`getUpdateHistory(key, ...)` and `scanUpdateHistory(...)` return finite,
newest-first pages when both history options are enabled and the history index
is ready. Continue with `UpdatesResponse.nextCursor`; the limit counts complete
record-version entries, independently of property-history `maxVersions`.
Both queries scan the existing model-wide history index. See
[history paging](../../core/docs/query.md#getscan-update-history) for filters,
projections, and hard-delete retention limits.

### Sensitive Field Encryption (Optional)

Mark a property as sensitive in a model:

```kotlin
val secret by string(index = 3u, sensitive = true)
```

Then configure a provider:

```kotlin
val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()

val store = FoundationDBDataStore.open(
    dataModelsById = mapOf(1u to MyModel),
    fieldEncryptionProvider = AesGcmHmacSha256EncryptionProvider(
        encryptionKey = keyMaterial.encryptionKey,
        tokenKey = keyMaterial.tokenKey
    )
)
```

Provider contracts live in shared module:
- `maryk.datastore.shared.encryption.FieldEncryptionProvider`
- `maryk.datastore.shared.encryption.SensitiveIndexTokenProvider` (needed for sensitive+unique)

Notes:
- Sensitive values are encrypted in table value payloads (latest + historic).
- Reads auto-decrypt based on an encrypted payload marker.
- Supported for simple value properties.
- Sensitive+`unique` is supported when `fieldEncryptionProvider` also implements `SensitiveIndexTokenProvider`.
- Sensitive properties cannot contribute to primary keys or secondary indexes,
  including composite and transformed indexes. Use a separate explicit blind-index
  field when search is required. Custom indexes without a dependency contract are
  rejected when the model contains sensitive properties.
- Earlier versions could accept unsafe composite indexes. This validation does not
  erase existing plaintext key/index bytes or physical backups. Recover affected
  data into a fresh store with safe definitions, then securely retire old copies.

## Cluster-wide live updates

By default, `executeFlow` receives updates from the current process. Enable the
optional cluster update log to propagate updates across processes using the same
cluster and `directoryPath`. It requires a stable consumer ID, and cannot be used
with models containing sensitive properties. Retention limits catch-up after outages.

Follow [Cluster-wide live updates](docs/cluster-updates.md) for configuration,
coordinated upgrades and monitoring.

## Operational Tips

- Ordinary requests: each add, change, or delete object is handled in its own FDB transaction; a multi-object request can therefore partially succeed. FDB retries conflicts, while Maryk returns validation errors (uniques, parent presence, etc.) as per-object statuses.
- Close: active transactions and futures are canceled before the native handle closes. Because the JVM FoundationDB binding does not guarantee that an in-flight native commit responds to `Transaction.close()`, scope shutdown is bounded to five seconds. A timeout is reported as `StorageException`; native work may still be in flight and must not be assumed rolled back.
- Scans: index scans are recommended for large filtered queries. Primary key scans are inexpensive for full‑range iteration. A request keeps one coherent FoundationDB snapshot; after four seconds the store throws `FoundationDBSnapshotExpiredException` before FoundationDB can reject the old version. Retry with a smaller page and use the scan cursor to resume completed pages.
- Historic queries: `toVersion` is supported for data, unique, and index reads. Historic index scanning is implemented and used when `toVersion` is provided.

## Development

You can run the tests locally using the local FDB server. See [Local Testing](./docs/local-testing.md) for more details.

Relevant code:
- Entry: [FoundationDBDataStore.kt](src/commonMain/kotlin/maryk/datastore/foundationdb/FoundationDBDataStore.kt)
- Processors: [processors](src/commonMain/kotlin/maryk/datastore/foundationdb/processors/)
- Helpers: [processor helpers](src/commonMain/kotlin/maryk/datastore/foundationdb/processors/helpers/)

Run module tests:
```bash
./gradlew :store:foundationdb:jvmTest
```

If you use a non‑default cluster file for tests, ensure `fdb.cluster` is present (the test config references `./fdb.cluster`).
Environment variable `FDB_CLUSTER_FILE` is set by Gradle to `store/foundationdb/fdb.cluster` for JVM tests.

## Hard deletion and history

A hard delete permanently removes a record's current and historic values, including when `keepAllVersions` is enabled. An earlier `toVersion` query cannot recover erased values. Use soft deletion when historical recovery is required.

See [store behavior](../shared/docs/store-behavior.md) for undelete, unique
ownership, replication ordering and scans that include deleted records.

## Serializable transaction scopes

FoundationDB uses one pinned native transaction per replay attempt, including reads, mutations, the revision fence and commit receipt. Preview aborts the native transaction. Application suspension retains no FoundationDB snapshot. Native attempts use a 4.5-second timeout, 4 MiB write budget and 10 retries; logical scopes use the shared 30-second default.

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

## License

Maryk is licensed under the Apache 2.0 License. See the repository's LICENSE file for details.

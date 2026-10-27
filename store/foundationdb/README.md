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

## Migrations and Update Handling

On startup, the engine checks stored model definitions against the running models. Compatible changes are applied automatically; incompatible changes require migration hooks. A `versionUpdateHandler` can perform post‑migration tasks.

For the full migration model, hook contracts, runtime phases, control APIs, lease behavior, and operational guidance, see [Migrations](./docs/migrations.md).

Configure FoundationDBDataStore.open with:
- pass migration settings as `migrationConfiguration = MigrationConfiguration(...)`
- pass FoundationDB lease tuning as `migrationLeaseConfiguration = FoundationDBMigrationLeaseConfiguration(...)`
- pass cluster log settings as `clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(...)`
- put `migrationHandler`, `migrationExpandHandler`, `migrationVerifyHandler`, and `migrationContractHandler` inside `migrationConfiguration`

```kotlin
suspend fun openStore() = FoundationDBDataStore.open(
    keepAllVersions = true,
    keepUpdateHistoryIndex = true,
    directoryPath = listOf("maryk", "app"),
    dataModelsById = mapOf(1u to Account),
    migrationConfiguration = MigrationConfiguration(
        migrationHandler = { context ->
            val fdbStore = context.store
            val storedModel = context.storedDataModel
            val newModel = context.newDataModel
            // return Success when handled
            when (newModel) {
                is Account -> MigrationOutcome.Success // example
                else -> MigrationOutcome.Fatal("Unsupported model")
            }
        }
    ),
    versionUpdateHandler = { fdbStore, storedModel, newModel ->
        // seed or backfill after a successful migration/update
    }
)
```

### Migration Lease Behavior

FoundationDB default lease is distributed (`FoundationDBMigrationLease`):
- Keyed per model in metadata subspace.
- Owner token + TTL (`migrationLeaseConfiguration.migrationLeaseTimeoutMs`).
- Background heartbeat (`migrationLeaseConfiguration.migrationLeaseHeartbeatMs`) renews lease while migration is active.
- Allows takeover after TTL expiry if migrator dies.

You can inject a custom `migrationLease` if needed.

## Configuration

- `keepAllVersions`: Mirror latest writes into historic subspaces for time travel and change history.
- `keepUpdateHistoryIndex`: Add a per-model `update_history` subspace keyed by change version + key for historical paging. `ScanUpdates` may select records through this index, but its emitted synchronization events remain chronological.
- `fdbClusterFilePath`: Optional path to an FDB cluster file; uses default environment if null.
- `directoryPath`: Subspace root path under which model directories are created.
- `databaseOptionsSetter`: Lambda executed once during startup on the underlying `DatabaseOptions`. Use this to enable tracing, tweak locality, or set transaction logging limits without forking Maryk.
- `clusterUpdateLogConfiguration.enableClusterUpdateLog`: Persist each local write (add/change/delete) into an FDB-backed update log and tail that log back into this process to drive `executeFlow` listeners across a whole cluster (multi-writer, multi-reader). It cannot be enabled when a registered model contains sensitive properties because cluster-log payload encryption is not yet supported.
- `clusterUpdateLogConfiguration.clusterUpdateLogConsumerId`: Required when cluster update logging is enabled. Must be unique per node/process (cursor stored under `__updates__/v1/consumers/<id>`).
- `clusterUpdateLogConfiguration.clusterUpdateLogOriginId`: Optional. Defaults to the consumer id. Used to skip “echo” of updates written by this same node when tailing.
- `clusterUpdateLogConfiguration.clusterUpdateLogShardCount`: Number of log shards (per store root). Higher spreads write hot-spotting; tailers read per-shard cursors. The first enabled open persists this value; later opens must match it. Changing it requires an explicit offline log migration so backlog and retention GC remain reachable.
- `clusterUpdateLogConfiguration.clusterUpdateLogRetention`: Time window to keep log entries (default 1 hour). A background job clears old ranges by timestamp.
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

## Cluster-Wide ExecuteFlow Updates (Optional)

By default, `executeFlow` only receives updates originating from the current process (in-memory update flow).

Enable the cluster update log to propagate updates between multiple processes connected to the same FoundationDB cluster + `directoryPath`:

```kotlin
val store = FoundationDBDataStore.open(
    directoryPath = listOf("maryk", "app"),
    dataModelsById = mapOf(1u to Account),
    clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
        enableClusterUpdateLog = true,
        clusterUpdateLogConsumerId = "node-1",
    ),
)
```

Typical use cases:
- Multiple app nodes serving realtime subscriptions: any node write becomes visible to listeners on all nodes.
- Read/write split: API nodes listen for updates while worker nodes write in background.
- Service decomposition: independent services share one Maryk store root but still receive consistent update events.
- Short catch-up after restart/outage: consumer cursor resumes inside retention window.

Notes:
- Models containing sensitive properties cannot use the cluster update log. Startup rejects this combination rather than persisting logical update payloads in plaintext.
- Uses FDB itself (append-only, sharded) and writes the log entry in the same transaction as the data mutation.
- Retention is time-based. If a node is offline longer than the retention window, it will resume at the retention cutoff (no replay beyond retention).
- Cluster HLC sync:
  - writers update `__updates__/v1/hlc_max/<shard>` using FDB atomic `BYTE_MAX` (8-byte big-endian HLC), so cluster max advances without read-modify-write contention or per-consumer marker growth.
  - each node runs a background HLC syncer (independent from update listeners) which watches heads and refreshes the `hlc_max/*` values to keep local version generation safely at/above the cluster floor.
- `clusterUpdateLogConfiguration.clusterUpdateLogConsumerId` should be stable per node/process across restarts. Changing it creates a fresh cursor and can duplicate delivery for up to the retention window.
- Log keys include `modelId` early, so consumers can range-scan only the models they care about.
- Upgrading from the legacy HLC-ordered cluster log to the commit-ordered log is a coordinated operation: stop or quiesce every reader and writer, upgrade all binaries, then restart them. Consumers drain persisted legacy entries before storing a one-way commit-ordered cursor. Do not run mixed old/new binaries or roll back after that cursor is stored; old binaries cannot enforce or understand the transition.

Observability:
- `FoundationDBDataStore.getClusterUpdateLogStats()` exposes tail/GC counters, HLC sync counters/backoff, last activity timestamps, observed cluster HLC, and active listener counts per model.
- Use it to detect stalled tailers (`lastDecodedAtUnixMs` / `lastTailAtUnixMs`), error spikes (`tailErrors` / `gcErrors`), or unnecessary tail load (`tailTransactions` growth).

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

## License

Maryk is licensed under the Apache 2.0 License. See the repository's LICENSE file for details.

### Hard deletion and history

A hard delete permanently removes a record's current and historic values, including when `keepAllVersions` is enabled. An earlier `toVersion` query cannot recover erased values. Use soft deletion when historical recovery is required.


## Serializable transaction scopes

FoundationDB uses one pinned native transaction per replay attempt, including reads, mutations, the revision fence and commit receipt. Preview aborts the native transaction. Application suspension retains no FoundationDB snapshot. Native attempts use a 4.5-second timeout, 4 MiB write budget and 10 retries; logical scopes use the shared 30-second default.

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

FoundationDB cluster logs distinguish old/new packed-value
payloads in an existing type bit; old queued payloads can be read without a queue
rewrite or extra log-envelope bytes.

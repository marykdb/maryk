# Maryk FoundationDB Store implementation

A FoundationDB-backed implementation of the Maryk data store. This engine maps Maryk's data model and request APIs onto FoundationDB's ordered key/value space using subspaces (directories), transactions, and efficient range reads.

See also:
- [Storage layout](./docs/storage.md)
- [Architecture overview](./docs/architecture.md)
- [Local testing](./docs/local-testing.md)

## Getting Started

Add the FoundationDB store to your application and open a datastore. At minimum you pass a map of DataModels (by id) and optionally whether to keep all versions.

```kotlin
suspend fun main() {
    val store = FoundationDBDataStore.open(
        keepAllVersions = true,                     // keep historic versions
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

On startup, the engine checks stored model definitions against the running models. When changes require a migration, you must supply a `migrationHandler`. A `versionUpdateHandler` can perform post‑migration tasks.

```kotlin
suspend fun openStore() = FoundationDBDataStore.open(
    keepAllVersions = true,
    directoryPath = listOf("maryk", "app"),
    dataModelsById = mapOf(1u to Account),
    migrationHandler = { fdbStore, storedModel, newModel ->
        // return true when handled successfully, false to abort
        when (newModel) {
            is Account -> true // example
            else -> false
        }
    },
    versionUpdateHandler = { fdbStore, storedModel, newModel ->
        // seed or backfill after a successful migration/update
    }
)
```

Model changes that generally do NOT require a migration: adding models, indexes, properties, or relaxing validation. Changes that DO: changing property types, renaming without alternatives, or tightening validation (these must go through migrations).

## Configuration

- `keepAllVersions`: Mirror latest writes into historic subspaces for time travel and change history.
- `fdbClusterFilePath`: Optional path to an FDB cluster file; uses default environment if null.
- `tenantName`: Optional `Tuple` to open a tenant.
- `directoryPath`: Subspace root path under which model directories are created.
- `databaseOptionsSetter`: Lambda executed once during startup on the underlying `DatabaseOptions`. Use this to enable tracing, tweak locality, or set transaction logging limits without forking Maryk.
- `enableClusterUpdateLog`: Persist each local write (add/change/delete) into an FDB-backed update log and tail that log back into this process to drive `executeFlow` listeners across a whole cluster (multi-writer, multi-reader).
- `clusterUpdateLogConsumerId`: Required when `enableClusterUpdateLog = true`. Must be unique per node/process (cursor stored under `__updates__/v1/consumers/<id>`).
- `clusterUpdateLogOriginId`: Optional. Defaults to `clusterUpdateLogConsumerId`. Used to skip “echo” of updates written by this same node when tailing.
- `clusterUpdateLogShardCount`: Number of log shards (per store root). Higher spreads write hot-spotting; tailers read per-shard cursors.
- `clusterUpdateLogRetention`: Time window to keep log entries (default 1 hour). A background job clears old ranges by timestamp.
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
- Sensitive+indexed is not supported.

## Cluster-Wide ExecuteFlow Updates (Optional)

By default, `executeFlow` only receives updates originating from the current process (in-memory update flow).

Enable the cluster update log to propagate updates between multiple processes connected to the same FoundationDB cluster + `directoryPath`:

```kotlin
val store = FoundationDBDataStore.open(
    directoryPath = listOf("maryk", "app"),
    dataModelsById = mapOf(1u to Account),
    enableClusterUpdateLog = true,
    clusterUpdateLogConsumerId = "node-1",
)
```

Typical use cases:
- Multiple app nodes serving realtime subscriptions: any node write becomes visible to listeners on all nodes.
- Read/write split: API nodes listen for updates while worker nodes write in background.
- Service decomposition: independent services share one Maryk store root but still receive consistent update events.
- Short catch-up after restart/outage: consumer cursor resumes inside retention window.

Notes:
- Uses FDB itself (append-only, sharded) and writes the log entry in the same transaction as the data mutation.
- Retention is time-based. If a node is offline longer than the retention window, it will resume at the retention cutoff (no replay beyond retention).
- Cluster HLC sync:
  - writers record their latest emitted HLC under `__updates__/v1/hlc/<clusterUpdateLogConsumerId>`.
  - writers also update `__updates__/v1/hlc_max/<shard>` using FDB atomic `BYTE_MAX` (8-byte big-endian HLC), so cluster max advances without read-modify-write contention.
  - each node runs a background HLC syncer (independent from update listeners) which watches heads and refreshes `max(hlc_max/*, hlc/*)` to keep local version generation safely at/above cluster floor.
- `clusterUpdateLogConsumerId` should be stable per node/process across restarts. Changing it creates a fresh cursor (possible duplicate delivery for up to retention) and a new HLC marker key.
- Log keys include `modelId` early, so consumers can range-scan only the models they care about.

Observability:
- `FoundationDBDataStore.getClusterUpdateLogStats()` exposes tail/GC counters, HLC sync counters/backoff, last activity timestamps, observed cluster HLC, and active listener counts per model.
- Use it to detect stalled tailers (`lastDecodedAtUnixMs` / `lastTailAtUnixMs`), error spikes (`tailErrors` / `gcErrors`), or unnecessary tail load (`tailTransactions` growth).

## Operational Tips

- Transactions: each request is handled within an FDB transaction; FDB retries on conflicts, while Maryk handles validation errors (uniques, parent presence, etc.).
- Scans: index scans are recommended for large filtered queries. Primary key scans are inexpensive for full‑range iteration.
- Historic queries: `toVersion` is supported for data, unique, and index reads. Historic index scanning is implemented and used when `toVersion` is provided.

## Development

You can run the tests locally using the local FDB server. See [Local Testing](./docs/local-testing.md) for more details.

Relevant code:
- Entry: [./src/commonMain/kotlin/maryk/datastore/foundationdb/FoundationDBDataStore.kt](./src/commonMain/kotlin/maryk/datastore/foundationdb/FoundationDBDataStore.kt)
- Processors: [./src/commonMain/kotlin/maryk/datastore/foundationdb/processors/](./src/commonMain/kotlin/maryk/datastore/foundationdb/processors/)
- Helpers: [./src/commonMain/kotlin/maryk/datastore/foundationdb/processors/helpers/](./src/commonMain/kotlin/maryk/datastore/foundationdb/processors/helpers/)

Run module tests:
```bash
./gradlew :store:foundationdb:jvmTest
```

If you use a non‑default cluster file for tests, ensure `fdb.cluster` is present (the test config references `./fdb.cluster`).
Environment variable `FDB_CLUSTER_FILE` is set by Gradle to `store/foundationdb/fdb.cluster` for JVM tests.

## License

Maryk is licensed under the Apache 2.0 License. See the repository's LICENSE file for details.

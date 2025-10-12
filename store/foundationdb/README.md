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

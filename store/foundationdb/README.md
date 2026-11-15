# Maryk FoundationDB Store

Distributed transactional storage for Maryk models, queries, history and live
updates. The store uses FoundationDB's ordered keyspace and native transactions.
It supports JVM and desktop Native targets where `libfdb_c` is available.

## Getting started

Install a compatible FoundationDB client and connect to a running cluster.
The current source targets client **7.3.81**, API version **730**, and Kotlin
multiplatform binding **7.3.81.1**. See [local setup](docs/local-testing.md)
for the managed development cluster.

Define a model and open a store with stable model IDs and a directory path:

```kotlin
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.requests.add
import maryk.datastore.foundationdb.FoundationDBDataStore

object Note : RootDataModel<Note>() {
    val message by string(index = 1u)
}

suspend fun main() {
    val store = FoundationDBDataStore.open(
        fdbClusterFilePath = "./fdb.cluster",
        directoryPath = listOf("maryk", "notes"),
        dataModelsById = mapOf(1u to Note),
    )
    try {
        val response = store.execute(Note.add(Note.create { message with "Hello Maryk" }))
        println(response.statuses)
    } finally {
        store.close()
    }
}
```

Omit `fdbClusterFilePath` to use FoundationDB's default cluster-file lookup.
Native hosts also own [process-wide client shutdown](docs/architecture.md#process-lifecycle).
Use matching Maryk module versions when consuming published artifacts.

## Behavior and limits

- Ordinary multi-object writes commit each record independently and can partially
  succeed. Use an explicit [transaction or atomic request](../../core/docs/query.md#choosing-a-write-boundary)
  for one commit across records or models.
- Reads use a consistent native snapshot with a short lifetime. Smaller pages
  and projections help stay within snapshot and read-byte limits. See
  [read limits and paging](docs/configuration.md#bounded-reads-bulk-import-and-maintenance).
- History queries require `keepAllVersions`; newest-first update-history pages
  also require `keepUpdateHistoryIndex` and a ready history index. Hard deletion
  permanently erases the record's current and historic values.
- `executeFlow` receives local-process updates by default. Cross-process updates
  require the optional [cluster update log](docs/cluster-updates.md).
- A timeout or cancellation during commit does not prove rollback. Retain a
  stable transaction ID and handle when [commit resolution](../../core/docs/query/transactions.md)
  is required. Ordinary retry markers protect retries within one invocation.

## Guides

| Task | Guide |
| --- | --- |
| Configure transaction policies, memory, read limits, bulk import or encryption | [Configuration and limits](docs/configuration.md) |
| Upgrade storage or model definitions; resume migrations | [Migrations](docs/migrations.md) and [storage-format upgrades](../shared/docs/storage-format-upgrades.md) |
| Propagate live updates across processes | [Cluster-wide live updates](docs/cluster-updates.md) |
| Back up or recover a cluster | [Native backup and recovery](docs/backups.md) |
| Transfer current state or retained history between backends | [Portable backups](../shared/docs/backup-restore.md) |
| Understand the engine | [Architecture](docs/architecture.md) and [storage layout](docs/storage.md) |
| Run local tests or develop the binding | [Local testing](docs/local-testing.md) |

Opening an existing store can run storage-format and schema migrations. Stop old
writers and retain a recoverable copy before upgrading. For a directory tree
copied into a separately configured directory layer, pass that same
`directoryLayer` to `open`, `readStoredModelDefinitionsFromDirectory` and
`detectStoredKeepAllVersions`. Opening a store does not perform the offline copy.

For optional SQL queries, follow the [SQL guide](../../sql/README.md). Expose a
store to remote clients through the [Remote Store](../remote/README.md).

## License

Maryk is licensed under the Apache 2.0 License. See the repository's LICENSE file
for details.

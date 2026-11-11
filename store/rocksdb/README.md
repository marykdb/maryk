# Maryk RocksDB Store

An embedded Maryk data store backed by [RocksDB](https://rocksdb.org), supporting
JVM, Android, Apple platforms, Linux, Windows and Android Native through the
`rocksdb-multiplatform` bindings.

## Quick start

Define a model, register its stable ID, and execute Maryk requests:

```kotlin
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.datastore.rocksdb.RocksDBDataStore

object Note : RootDataModel<Note>() {
    val message by string(index = 1u)
}

suspend fun main() {
    RocksDBDataStore.open(
        relativePath = "notes.db",
        dataModelsById = mapOf(1u to Note),
    ).use {
        execute(Note.add(Note.create { Note.message with "Hello Maryk" }))
    }
}
```

The path is a database directory. Keep model IDs stable across reopens. `use`
closes the store; otherwise, call `close()` when finished to release native resources.

The defaults retain history and use a shared 64 MiB cache, 10-bit Bloom filters,
automatic index search for bytewise families, and LZ4/Zstandard compression.
Existing stores remain readable; opening can run a required storage-format or
schema migration. Read the [upgrade guidance](documentation/migrations.md#opening-existing-stores)
before upgrading a deployed store.

## Guides

| Task | Guide |
| --- | --- |
| Tune memory, filters, compression, caches or logging; inspect diagnostics | [Configuration and tuning](documentation/configuration.md) |
| Configure history, page revisions or use transactions | [History and transactions](documentation/history-and-transactions.md) |
| Encrypt sensitive properties and manage key ownership | [Field encryption](documentation/encryption.md) |
| Upgrade storage or migrate model definitions | [Migrations and upgrades](documentation/migrations.md) |
| Create and restore physical recovery backups | [RocksDB backups](documentation/backups.md) |
| Transfer data between storage backends | [Portable backups](../shared/docs/backup-restore.md) |
| Understand the implementation | [Architecture](documentation/architecture.md) and [storage layout](documentation/storage.md) |

For request examples, see [reading and writing](../../core/docs/query/reads-writes.md).
Expose a store through the [Remote Store](../remote/README.md), or add
[SQL queries](../../sql/README.md) when needed.

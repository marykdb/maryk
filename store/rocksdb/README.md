# Maryk RocksDB Store

An embedded, high‑performance Maryk data store built on [RocksDB](https://rocksdb.org).

Each DataModel is mapped to multiple column families (keys/table/index/unique and historic variants when enabled). See the detailed [Architecture](documentation/architecture.md) and [Storage Layout](documentation/storage.md).

## Getting Started

To get started with the Maryk RocksDB Store, simply use the following code snippet:

Usage:
```kotlin
RocksDBDataStore.open(
    keepAllVersions = false,
    relativePath = "path/to/folder/on/disk/for/store", 
    dataModelsById = mapOf(
        1u to Account,
        2u to Course
    )
).use { store ->
    // Do operations on the store
    
    store.execute(
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
}
```

The `.use { ... }` scope closes the store automatically. If you don’t use `use`, call `close()` when finished to free native resources.

Need remote access to this local RocksDB store? Expose it with the [Remote Store](../remote/README.md) via CLI `serve`.

## Migrations and Version Updates

On open, the store compares the stored model with your provided definitions and decides whether it is up‑to‑date, requires safe additions (including new indexes), or needs a migration. For incompatible changes you provide a `migrationHandler` that performs fixes and returns `true` to continue. After a successful update you can run custom logic via `versionUpdateHandler` (for example, seed data that depends on the new schema).

Notes:
- You can add models, properties, and indexes and relax validation without a migration.
- Changing property types, renaming without alternatives, or tightening validation triggers a migration.

```kotlin
RocksDBDataStore.open(
    // True if the data store should keep all past versions of the data
    keepAllVersions = true,
    relativePath = "path/to/folder/on/disk/for/store", 
    dataModelsById = mapOf(
        1u to Account,
        2u to Course
    ),
    migrationHandler = { rocksDBDataStore, storedDataModel, newDataModel ->
        // example 
        when (newDataModel) {
            is Account -> when(storedDataModel.version.major) {
                1 -> {
                    // Execute actions on rocksDBDataStore
                    true
                }
                else -> false
            }
            else -> false
        }
    },
    versionUpdateHandler = { rocksDBDataStore, storedDataModel, newDataModel ->
        // example 
        when (storedDataModel) {
            null -> Unit // Do something when model did not exist before
            else -> Unit
        }
    }
)
```

## Platform Support

This module is Kotlin Multiplatform and works on JVM, iOS, macOS, tvOS, watchOS, Android, Android Native, Windows and Linux via the `rocksdb-multiplatform` bindings.

For a deeper dive into how data is laid out and how queries execute, check the [Architecture](documentation/architecture.md) and [Storage Layout](documentation/storage.md) docs.

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
- Sensitive+indexed is not supported.
- Pass `fieldEncryptionProvider = encryptionProvider` to `RocksDBDataStore.open(...)`.

# Maryk RocksDB Store implementation

A [RocksDB](https://rocksdb.org) implementation of a Maryk data store. It stores the data
all in one RocksDB which separated data into multiple column families per model. Read more 
about how data is stored in [the storage description](documentation/storage.md).

Usage:
```kotlin
RocksDBDataStore(
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

Note: when a store is created ensure to call `close()` at the end of use to ensure the release
of used memory and cleaning up of processes.

## Migrations

The data store always checks on creation if all passed data models are compatible with the stored data models. 
If not it will trigger a migration. To handle a migration it is needed to define a `migrationHandler`.

Note: Models, indices and properties can be added without migration. It is also possible to set less strict validation
rules. It is not possible to change types of properties, rename values without making the old value alternative or add
more strict validation without automatically trigger a validation.

```kotlin
Rocks(
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
    }
)
```

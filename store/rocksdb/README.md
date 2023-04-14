# Maryk RocksDB Store implementation

A powerful implementation of Maryk data store using [RocksDB](https://rocksdb.org). 
This store organizes data into multiple column families in the format defined by the DataModel.
Learn more about the storage layout by reading [the storage description](documentation/storage.md).

## Getting Started

To get started with the Maryk RocksDB Store, simply use the following code snippet:

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

**Note:** It is important to call `close()` at the end of the use block to release memory and clean up any processes.

## Migrations

The data store always checks if all passed DataModels are compatible with the stored models upon creation. 
If they are not, it will trigger a migration. A `migrationHandler` must be defined to handle migrations.

**Note:** It is possible to add Models, indices, and properties without a migration. Additionally, it is possible 
to set less strict validation rules. However, it is not possible to change the types of properties, rename values 
without making the old value an alternative, or add more strict validation without automatically triggering a migration.

```kotlin
RocksDBDataStore(
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

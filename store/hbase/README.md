# Maryk HBase Store implementation

A powerful implementation of Maryk data store using [HBase](https://hbase.apache.org). 
This store organizes data into multiple column families in the format defined by the DataModel.
Learn more about the storage layout by reading [the storage description](documentation/storage.md).

## Getting Started

To get started with the Maryk Hbase Store, simply use the following code snippet. Be sure to pass a valid HBase connection.

You can also optionally pass a namespace to the store if you want to use a specific HBase namespace.

Usage:
```kotlin
HbaseDataStore.open(
    connection = connection,
    keepAllVersions = true,
    namespace = "OptionalNamespace",
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

**Note:** Be sure to call `close()` at the end of the use block to release memory and clean up any processes.

## Migrations and Update handling

The data store always checks if all passed DataModels are compatible with the stored models upon creation.
If they are not, it will trigger a migration. A `migrationHandler` must be defined to handle migrations.

There is also a `versionUpdateHandler` which enables you to do actions after an update/migration action
was successfully done. This way you can add intial data which depends on the updated models.

**Note:** It is possible to add Models, indices, and properties without a migration. Additionally, it is possible
to set less strict validation rules. However, it is not possible to change the types of properties, rename values
without having the old value an alternative, or add more strict validation without automatically triggering a migration.

```kotlin
HbaseDataStore.open(
    connection = connection,
    // True if the data store should keep all past versions of the data
    keepAllVersions = true,
    namespace = "OptionalNamespace",
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

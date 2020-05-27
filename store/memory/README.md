# Maryk Memory Store implementation

An in memory implementation of a Maryk data store. It works with an internal sorted list by key
of `DataRecord` which contain all values in its Kotlin representation.

Usage:
```kotlin
InMemoryDataStore(
    // True if the data store should keep all past versions of the data
    keepAllVersions = true, 
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

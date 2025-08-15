# Maryk Memory Store implementation

A high-performance in-memory implementation of a Maryk data store that uses an internal sorted list
of `DataRecord` to store data. It provides quick access to data, making it ideal for testing and
development purposes, where the focus is not on persistence.


## Usage

Here's a simple example of how to use the InMemoryDataStore:

```kotlin
InMemoryDataStore.open(
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

**Note:** It's important to call close() at the end of use to release used memory and clean up processes.

## Advantages

- Quick access to data
- Ideal for testing and development purposes
- Efficient storage of data in an internal sorted list

## Limitations

- The data is not persisted, so it is lost when the store is closed or the application shuts down.
- It may not be suitable for large data sets or production environments where data persistence is a concern.

# Maryk Memory Store

In-memory Maryk data store implementation.
Fastest option for tests and local development. No persistence.

## Getting started

```kotlin
InMemoryDataStore.open(
    keepAllVersions = true,
    dataModelsById = mapOf(
        1u to Account,
        2u to Course
    )
).use { store ->
    // Run operations on the store
    
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

The `.use { ... }` scope closes the store automatically.

## When to use

- Unit/integration tests.
- Local development.
- CI with deterministic behavior and zero external dependencies.

## Limitations

- Data is lost on close/shutdown.
- Not suitable as persistent production storage.

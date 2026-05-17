# Maryk DataFrame helpers

Provides DataFrame helper functions for Maryk Values objects so they can be used in Notebooks to play with the data.

This module is for exploratory analysis and notebook workflows. It is not required for normal Maryk modeling, storage or serialization.

## Usage

### Convert Maryk Values to DataFrame

```kotlin
val dataFrame = listOf(
    SimpleMarykObject("1", "value1"),
    SimpleMarykObject("2", "value2"),
    SimpleMarykObject("3", "value3")
).toDataFrame()
```

### Convert DataStore Get results to DataFrame

```kotlin
val result = dataStore.execute(
    Person.get(
        person1Key,
        person2Key
    )
)
    
val dataFrame = result.values.toDataFrame()
```

## Typical workflow

1. Query a Maryk store.
2. Convert returned values to a DataFrame.
3. Inspect, filter, group or chart in a notebook.

Keep production query logic in Maryk requests; use DataFrame conversion for analysis and diagnostics.

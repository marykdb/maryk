# Maryk DataFrame helpers

Provides DataFrame helper functions for Maryk Values objects so they can be used in Notebooks to play with the data.

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

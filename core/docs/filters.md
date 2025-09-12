# Filters

Filters narrow query results. They can be applied to [Get](query.md#get) and [Scan](query.md#scan) requests and combined to form complex logic. Use [`And`](#and) and [`Or`](#or) to combine conditions or [`Not`](#not) to invert them.

## Reference Filters

The filters below target a specific property via a [property reference](properties/references.md).

### Exists

Checks whether a value exists.

```kotlin
Exists(
    propertyReference1,
    propertyReference2,
    propertyReference3,
)
```

### Equals

Matches when a property equals the provided value.

```kotlin
Equals(
    stringPropertyReference with "value",
    numberPropertyReference with 5,
)
```

### GreaterThan

Matches when the value is greater than the provided value.

```kotlin
GreaterThan(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### GreaterThanEquals

Matches when the value is greater than or equal to the provided value.

```kotlin
GreaterThanEquals(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### LessThan

Matches when the value is less than the provided value.

```kotlin
LessThan(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### LessThanEquals

Matches when the value is less than or equal to the provided value.

```kotlin
LessThanEquals(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### Range

Checks whether the value falls within a range.

Maryk YAML:

```kotlin
Range(
    intPropertyReference with 2..42,
    stringPropertyReference with ValueRange(
        from = "abba",
        to = "zeplin",
        inclusiveTo = false,
    )
) 
```

### Prefix

Matches values that start with the given prefix.

```kotlin
Prefix(
    stringPropertyReference with "val",
    anotherStringPropertyReference with "do",
)
```

### RegEx

Matches values that satisfy a regular expression.

```kotlin
RegEx(
    stringPropertyReference with "[A-Z]+al.*",
    anotherStringPropertyReference with "[E-Z]+al.*",
)
```

### ValueIn

Matches when the value is included in a provided set.

```kotlin
ValueIn(
    stringPropertyReference with setOf("a", "b", "value"),
    intPropertyReference with setOf(1, 3, 5),
)
```

## Filter operations

Filter operations combine other filters to build complex queries.

### And

`And` returns `true` only if all included filters match.

```kotlin
And(
    Equals(
        stringPropertyReference with "value"
    ),
    GreaterThan(
        intPropertyReference with 42
    )
)
```

### Or

`Or` returns `true` if any of the included filters matches.

```kotlin
Or(
    Equals(
        stringPropertyReference with "value"
    ),
    GreaterThan(
        intPropertyReference with 42
    )
)
```

### Not

`Not` inverts the result of the nested filters. If multiple filters are provided it behaves as `And` and negates the combined result.

```kotlin
Not(
    Equals(stringPropertyReference equals "value"),
    Exists(intPropertyReference),
)
```

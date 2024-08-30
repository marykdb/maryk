# Filters

Filters are useful tools that can be applied to both [Get](query.md#get) and [Scan](query.md#scan) objects in queries.
You can create complex operations by using the [`And`](#and) and [`Or`](#or) filters, while also having the ability to
reverse filters with the [`Not`](#not) filter.

## Reference Filters

The following filters operate on a property within a data object, which is identified by a
[property reference](properties/references.md).

### Exists

This filter checks whether a value exists.

```kotlin
Exists(
    propertyReference1,
    propertyReference2,
    propertyReference3,
)
```

### Equals

Use this filter to check if a property reference's value is equal to a specified value.

```kotlin
Equals(
    stringPropertyReference with "value",
    numberPropertyReference with 5,
)
```

### GreaterThan

This filter checks if the referenced values are greater than a specific value.

```kotlin
GreaterThan(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### GreaterThanEquals

Use this filter to check if the referenced value is greater than or equal to a specified value.

```kotlin
GreaterThanEquals(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### LessThan

This filter checks if the referenced value is less than a particular value.

```kotlin
LessThan(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### LessThanEquals

Use this filter to check if the referenced value is less than or equal to a specified value.

```kotlin
LessThanEquals(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### Range

This filter checks if the referenced value falls within a specified range.

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

Use this filter to check if the referenced value starts with a given prefix.

```kotlin
Prefix(
    stringPropertyReference with "val",
    anotherStringPropertyReference with "do",
)
```

### RegEx

This filter checks if the referenced value matches a specified regular expression.

```kotlin
RegEx(
    stringPropertyReference with "[A-Z]+al.*",
    anotherStringPropertyReference with "[E-Z]+al.*",
)
```

### ValueIn

Use this filter to check if the referenced value is included in a set of specified values.

```kotlin
ValueIn(
    stringPropertyReference with setOf("a", "b", "value"),
    intPropertyReference with setOf(1, 3, 5),
)
```

## Filter operations

Filter operations provide powerful capabilities for constructing complex queries. They can run on top of other filters,
allowing you to create intricate and highly customizable search criteria.

### And

The And filter returns `true` if all specified filters match. This filter is ideal when you want to find records that
meet multiple conditions.

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

The Or filter returns `true` if at least one of the specified filters matches. This is useful for finding records that
satisfy any of several conditions.

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

The Not filter inverts the meaning of specified filters. If multiple filters are provided, it performs an And operation.
This filter is beneficial for excluding records that meet specific criteria.

```kotlin
Not(
    Equals(stringPropertyReference equals "value"),
    Exists(intPropertyReference),
)
```

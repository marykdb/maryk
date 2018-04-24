# Filters

Filters can be applied on [Get](query.md#get) and [Scan](query.md#scan) object
queries. It is possible to construct a series of operations by using [`And`](#and)
and [`Or`](#or) filters. The filter can also be reversed with a [`Not`](#not)
filter

## Reference Filters
These filters operate on a property and is triggered from a 
[property reference](properties/references.md).

### Exists
Checks if a value exists

```kotlin
propertyReference.exists()
```

### Equals
Checks if a value on a property reference is equal to given value.

```kotlin
stringPropertyReference equals "value"
```

### GreaterThan
Checks if referenced value is greater than the given value.

```kotlin
intPropertyReference greaterThan 42 
```

### GreaterThanEquals
Checks if referenced value is greater than or equal to the given value.

```kotlin
intPropertyReference greaterThanEquals 42 
```

### LessThan
Checks if referenced value is less than the given value.

```kotlin
intPropertyReference lessThan 42 
```

### LessThanEquals
Checks if referenced value is less than or equal to the given value.

```kotlin
intPropertyReference lessThanEquals 42 
```

### Range
Checks if referenced value is within the given range.

```kotlin
intPropertyReference inRange 2..42 
```

### Prefix
Checks if referenced value is prefixed by given value.

```kotlin
stringPropertyReference isPrefixedBy "val"
```

### RegEx
Checks if referenced value matches with given regular expression.

```kotlin
stringPropertyReference matchesRegEx "[A-Z]+al.*"
```

### ValueIn
Checks if referenced value is within the set of given values.

```kotlin
stringPropertyReference valueIn setOf("a", "b", "value")
```

## Filter operations
These filters run on top of other filters so they can provide a way to 
construct more complex queries.

### And
Returns true if all given filters match.

```kotlin
And(
    stringPropertyReference equals "value",
    intPropertyReference greaterThan 42 
)
```

### Or
Returns true if one of given filters match.

```kotlin
Or(
    stringPropertyReference equals "value",
    intPropertyReference greaterThan 42 
)
```

### Not
Returns true if given filter does not match

```kotlin
Not(stringPropertyReference equals "value")
```

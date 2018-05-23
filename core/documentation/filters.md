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

Maryk YAML:
```yaml
# Single check
!Exists propertyReference

# Check multiple properties
!Exists 
  - propertyReference1
  - propertyReference2
  - propertyReference3
```

Kotlin:
```kotlin
Exists(
    propertyReference1,
    propertyReference2,
    propertyReference3
)
```

### Equals
Checks if a value on a property reference is equal to given value.


Maryk YAML:
```yaml
!Equals
  stringPropertyReference: value
  numberPropertyReference: 5

```

Kotlin:
```kotlin
Equals(
    stringPropertyReference with "value",
    numberPropertyReference with 5
)
```

### GreaterThan
Checks if referenced values are greater than the given value.

Maryk YAML:
```yaml
!GreaterThan
  stringPropertyReference: value
  numberPropertyReference: 42
```

Kotlin:
```kotlin
GreaterThan(
    stringPropertyReference with "value",
    intPropertyReference with 42
)
```

### GreaterThanEquals
Checks if referenced value is greater than or equal to the given value.

Maryk YAML:
```yaml
!GreaterThanEquals
  stringPropertyReference: value
  numberPropertyReference: 42
```

Kotlin:
```kotlin
GreaterThanEquals(
    stringPropertyReference with "value",
    intPropertyReference with 42
)
```

### LessThan
Checks if referenced value is less than the given value.

Maryk YAML:
```yaml
!LessThan
  stringPropertyReference: value
  numberPropertyReference: 42

```

Kotlin:
```kotlin
LessThan(
    stringPropertyReference with "value",
    intPropertyReference with 42
)
```

### LessThanEquals
Checks if referenced value is less than or equal to the given value.

Maryk YAML:
```yaml
!LessThanEquals
  stringPropertyReference: value
  numberPropertyReference: 42
```

Kotlin:
```kotlin
LessThanEquals(
    stringPropertyReference with "value",
    intPropertyReference with 42
)
```

### Range
Checks if referenced value is within the given range.

Maryk YAML:
```yaml
!Range
  intPropertyReference: [2, 42]
  stringPropertyReference: [!Exclude abba, zeplin]
```

Kotlin:
```kotlin
Range(
    intPropertyReference with 2..42,
    stringPropertyReference with ValueRange(
        from = "abba",
        to = "zeplin",
        inclusiveTo = false
    )
) 
```

### Prefix
Checks if referenced value is prefixed by given value.

Maryk YAML:
```yaml
!Prefix
  stringPropertyReference: val
  anotherStringPropertyReference: do
```

Kotlin:
```kotlin
Prefix(
    stringPropertyReference with "val",
    anotherStringPropertyReference with "do"
)
```

### RegEx
Checks if referenced value matches with given regular expression.

Maryk YAML:
```yaml
!RegEx
  stringPropertyReference: [A-Z]+al.*
  anotherStringPropertyReference: [E-Z]+al.*
```

Kotlin:
```kotlin
RegEx(
    stringPropertyReference with "[A-Z]+al.*",
    anotherStringPropertyReference with "[E-Z]+al.*"
)
```

### ValueIn
Checks if referenced value is within the set of given values.

Maryk YAML:
```yaml
!ValueIn
  stringPropertyReference: [a, b, value],
  intPropertyReference: [1, 3, 5]
```

Kotlin:
```kotlin
ValueIn(
    stringPropertyReference with setOf("a", "b", "value"),
    intPropertyReference with setOf(1, 3, 5)
)
```

## Filter operations
These filters run on top of other filters so they can provide a way to 
construct more complex queries.

### And
Returns true if all given filters match.

Maryk YAML:
```yaml
!And
  - !Equals
    stringPropertyReference: value
  - !GreaterThan
    intPropertyReference: 42
```

Kotlin:
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
Returns true if one of given filters match.

Maryk YAML:
```yaml
!Or
  - !Equals
    stringPropertyReference: value
  - !GreaterThan
    intPropertyReference: 42
```

Kotlin:
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
Inverts the meaning of given filters. If passed multiple it will do an AND operation.

Maryk YAML:
```yaml
!Not
  - !Equals
    stringPropertyReference: value
  - !Exists intPropertyReference
```

Kotlin:
```kotlin
Not(
    Equals(stringPropertyReference equals "value"),
    Exists(intPropertyReference)
)
```

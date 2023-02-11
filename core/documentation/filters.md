# Filters

Filters can be applied to both [Get](query.md#get) and [Scan](query.md#scan) objects in
queries. Complex operations can be created by using the [`And`](#and)
and [`Or`](#or) filters. , and filters can be reversed using the [`Not`](#not)
filter.

## Reference Filters
The following filters operate on a property within a data object which is referred to with a 
[property reference](properties/references.md).

### Exists
Checks if a value exists.

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
Checks if a property reference's value is equal to the given value.

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
Checks if the referenced values are greater than the given value.

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
Checks if the referenced value is greater than or equal to the given value.

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
Checks if the referenced value is less than the given value.

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
Checks if the referenced value is less than or equal to the given value.

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
Checks if the referenced value is within the given range.

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
Checks if the referenced value is prefixed by the given value.

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
Checks if the referenced value matches with the given regular expression.

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
Checks if the referenced value is within the set of given values.

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
Filter operations are powerful tools that allow you to construct complex queries. 
They run on top of other filters, making it possible to create intricate and highly customizable search criteria.

### And
The And filter returns `true` if all the specified filters match. 
This is an ideal filter to use if you want to find records that meet multiple conditions.

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
The Or filter returns `true` if one of the specified filters matches. 
This is useful if you want to find records that match either of several conditions.

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
The Not filter inverts the meaning of the specified filters. If multiple filters are passed, it performs an And operation. 
This filter is useful if you want to exclude records that meet certain criteria.

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

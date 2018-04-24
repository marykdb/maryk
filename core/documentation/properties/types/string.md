# String
Basic String property to represent text.

- Maryk Yaml Definition: `String`
- Kotlin Definition: `StringDefinition`
- Kotlin Value : `String`

## Usage options
- Value
- Map Value
- Inside List/Set

## Validation Options
- `required` - default true
- `final` - default false
- `unique` - default false
- `regEx` - Regular expression to match complete value against
- `minSize` - The minimum length of the string. Default unset.
- `maxSize` - The maximum length of the string. Default unset.
- `minValue` - default false. Minimum value (Sort start value)
- `maxValue` - default false. Maximum value (Sort end value)

## Data options
- `indexed` - default false
- `searchable` - default true

**Example of a Kotlin String property definition**
```kotlin
val def = StringDefinition(
    required = true,
    final = true,
    unique = true,
    minSize = 3,
    maxSize = 6,
    minValue = "aab",
    maxValue = "ddda",
    regEx = "[abcd]{3,4}"
)
```

**Example of a YAML String property definition**
```yaml
!String
  required: false
  unique: false
  final: true
  minSize: 3
  maxSize: 6
  minValue: aab
  maxValue: ddda
  regEx: [abcd]{3,4}
```

## Storage/Transport Byte representation
Strings are stored as UTF-8 encoded bytes. With transport the LENGTH_DELIMITED wiretype is used

## String representation
String

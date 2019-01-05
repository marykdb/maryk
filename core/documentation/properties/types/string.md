# String
Basic String property to represent text.

- Maryk Yaml Definition: `String`
- Kotlin Definition: `StringDefinition`
- Kotlin Value : `String`

## Usage options
- Value
- Map Key or Value
- Inside List/Set

## Validation Options
- `required` - default true
- `final` - default false
- `unique` - default false
- `regEx` - regular expression to match complete value against
- `minSize` - minimum length of the string. Default unset.
- `maxSize` - maximum length of the string. Default unset.
- `minValue` - default false. Minimum value (Sort start value)
- `maxValue` - default false. Maximum value (Sort end value)

## Other options
- `default` - the default value to be used if value was not set.
- `indexed` - default false

## Examples

**Example of a YAML String property definition**
```yaml
!String
  required: false
  unique: false
  final: true
  minSize: 3
  maxSize: 6
  default: baa
  minValue: aab
  maxValue: ddda
  regEx: [abcd]{3,4}
```

**Example of a Kotlin String property definition**
```kotlin
val def = StringDefinition(
    required = true,
    final = true,
    unique = true,
    minSize = 3,
    maxSize = 6,
    default = "baa",
    minValue = "aab",
    maxValue = "ddda",
    regEx = "[abcd]{3,4}"
)
```

## Storage/Transport Byte representation
Strings are stored as UTF-8 encoded bytes. With transport the LENGTH_DELIMITED wiretype is used

## String representation
String

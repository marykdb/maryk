# Boolean
Boolean true or false.

- Maryk Yaml Definition: `Boolean`
- Kotlin Definition: `BooleanDefinition`
- Kotlin Value: `Boolean`

## Usage options
- Value
- Map Key or Value
- Inside List/Set

## Validation Options
- `required` - default true
- `final` - default false

## Other options
- `default` - the default value to be used if value was not set.
- `indexed` - default false

## Examples

**Example of a YAML Boolean property definition**
```yaml
!Boolean
  required: false
  final: true
  default: true
```

**Example of a Kotlin Boolean property definition**
```kotlin
val def = BooleanDefinition(
    indexed = true,
    required = true,
    final = false,
    default = true
)
```

## Storage and Transport Byte representation
`0b0000` for false `0b0001` for true

## String representation
`true` or `false`

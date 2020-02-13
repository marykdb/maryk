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

## Examples

**Example of a YAML Boolean property definition**
```yaml
!Boolean
  required: false
  final: true
  default: true
```

**Example of a Kotlin Boolean property definition for use within a Model its PropertyDefinitions**
```kotlin
val isActivated by boolean(
    index = 1u,
    required = true,
    final = false,
    default = true
)
```

**Example of a Kotlin Boolean property definition**
```kotlin
val def = BooleanDefinition(
    required = true,
    final = false,
    default = true
)
```

## Storage and Transport Byte representation
`0b0000` for false `0b0001` for true

## String representation
`true` or `false`

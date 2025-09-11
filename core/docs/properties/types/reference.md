# Reference
Property representing a Reference to another DataObject.

- Kotlin Definition: `ReferenceDefinition<T>` In which T is the DataModel name
- Kotlin Value : `Key<T>` In which T is the DataModel
- Maryk Yaml Definition: `Reference`

## Usage options
- Value
- Map key or value
- Inside List/Set

## Validation Options
- `required` - default true
- `final` - default false
- `unique` - default false
- `minValue` - default false. Minimum value
- `maxValue` - default false. Maximum value

## Other options
- `default` - the default value to be used if value was not set.
- `dataModel` - Model of DataObjects to be referred to

## Examples

**Example of a Reference property definition for use within a Model**
```kotlin
val friend by reference(
    index = 1u,
    dataModel = { Person },
    required = false,
    final = true,
    unique = true
)
```

**Example of a separate Reference property definition**
```kotlin
val def = ReferenceDefinition(
    required = true,
    final = true,
    unique = true,
    dataModel = { Person }
)
```

## Storage/Transport Byte representation
The key of the referenced DataObject as bytes. With transport the Length Delimited
wiretype is used

## String representation
Key as base64 value

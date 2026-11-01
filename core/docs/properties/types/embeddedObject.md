# Embedded Object Property
*Note:* This type can only be used in object DataModels defined in code and cannot be used for queries.

A property which contains another DataModel as embedded object. See 
[DataModels](../../datamodel.md) for more details on how to define DataModels.

- Kotlin Definition: `EmbeddedObjectDefinition`
- Kotlin Value: `T` T stands for the DataModel data class 

## Usage options
- Value
- Map value

## Validation Options
- `required` - default true
- `final` - default false

## Other options
- `default` - the default value to be used if value was not set.
- `dataModel` - Refers to DataModel to be embedded

## Examples

**Example of an Embedded Object property definition for use within a Model**
```kotlin
val address by embedObject(
    index = 1u,
    required = false,
    final = true,
    dataModel = { Address }
)
```

**Example of a separate EmbeddedObject property definition**
```kotlin
val def = EmbeddedObjectDefinition(
    required = false,
    final = true,
    dataModel = { Address }
)
```

## Transport Byte representation
Uses the [embedded-message encoding](../../serialization/protobuf.md#embedded-messages).
The nested model determines the property indexes and wire types within the payload.

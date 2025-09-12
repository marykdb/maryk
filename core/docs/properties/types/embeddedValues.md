# Embedded Values Property
A property that contains values from a selected DataModel. See
[DataModels](../../datamodel.md) for more details on how to define DataModels.

- Kotlin Definition: `EmbeddedValuesDefinition<DM, P>` DM is DataModel and P the properties
- Kotlin Value: `Values<DM, P>` DM is DataModel and P the properties 
- Maryk Yaml Definition: `Embed`

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

**Example of an Embedded Values property definition for use within a Model**
```kotlin
val address by embed(
    index = 1u,
    required = false,
    final = true,
    dataModel = { Address }
)
```

**Example of a separate Embedded Values property definition**
```kotlin
val def = EmbeddedValuesDefinition(
    required = false,
    final = true,
    dataModel = { Address }
)
```

## Transport Byte representation
All fields of a DataObject are wrapped in a tag/value pair with LENGTH_DELIMITED
wiretype and the value starts with the length of the total bytes of the DataObject

# EmbeddedObject Property
A property which contains values from selected DataModel. See 
[DataModels](../../datamodel.md) for more details on how to define DataModels.

- Maryk Yaml Definition: `Embed`
- Kotlin Definition: `EmbeddedValuesDefinition<DM, P>` DM is DataModel and P the properties
- Kotlin Value: `Values<DM, P>` DM is DataModel and P the properties 

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

**Example of a YAML EmbeddedObject property definition**
```yaml
!Embed
  dataModel: Address
  required: false
  final: true
```

**Example of a Kotlin EmbeddedObject property definition**
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

# EmbeddedObject Property
A property which contains another DataModel as embedded object. See 
[DataModels](../../datamodel.md) for more details on how to define DataModels.

- Maryk Yaml Definition: `EmbeddedObject` T is name of model
- Kotlin Definition: `EmbeddedObjectDefinition<T>` T is for the name of DataModel
- Kotlin Value: `T` T stands for the DataModel data class 

## Usage options
- Value
- Map value

## Validation Options
- `required` - default true
- `final` - default false

## Other options
- `default` - the default value to be used if value was not set.
- `indexed` - default false
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
val def = EmbeddedObjectDefinition(
    required = false,
    final = true,
    dataModel = Address
)
```

## Transport Byte representation
All fields of a DataObject are wrapped in a tag/value pair with LENGTH_DELIMITED
wiretype and the value starts with the length of the total bytes of the DataObject

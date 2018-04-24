# SubModel Property
A property which contains another DataModel as embedded object. See 
[DataModels](../../datamodel.md) for more details on how to define DataModels.

- Maryk Yaml Definition: `SubModel` T is name of model
- Kotlin Definition: `SubModelDefinition<T>` T is for the name of DataModel
- Kotlin Value: `T` T stands for the DataModel data class 

## Usage options
- Value
- Map value

## Validation Options
- `required` - default true
- `final` - default false

## Data options
- `indexed` - default false
- `searchable` - default true
- `dataModel` - Refers to DataModel to be embedded

## Examples

**Example of a Kotlin SubModel property definition**
```kotlin
val def = SubModelDefinition(
    required = false,
    final = true,
    dataModel = Address
)
```

**Example of a YAML SubModel property definition**
```yaml
!SubModel
  dataModel: Address
  required: false
  final: true
```

## Transport Byte representation
All fields of a DataObject are wrapped in a tag/value pair with LENGTH_DELIMITED
wiretype and the value starts with the length of the total bytes of the DataObject

# SubModel Property
A property which contains another DataModel as embedded object. See 
[DataModels](../datamodel.md) for more details on how to define DataModels.

- Maryk Yaml Definition: **Model<T>** T is name of model
- Kotlin Definition : **SubModelDefinition<T>** T is for the name of DataModel
- Kotlin Value: **T** T stands for the DataModel data class 

## Usage options
- Value
- Map value

## Validation Options
- Required
- Final

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true
- dataModel - Refers to DataModel to be embedded

**Example of a kotlin SubModel definition**
```kotlin
val def = SubModelDefinition(
    name = "address",
    index = 0,
    required = false,
    final = true,
    dataModel = Address
)
```

## Transport Byte representation
All fields of a DataObject are wrapped in a tag/value pair with LENGTH_DELIMITED
wiretype and the value starts with the length of the total bytes of the DataObject
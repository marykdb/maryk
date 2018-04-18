# Value Property
A property which contains another DataModel as a value. See 
[DataModels](../datamodel.md) for more details on how to define DataModels.

ValueDataModel objects are stored and transported as fixed length byte objects.
This makes them usable as map keys and list/set items.

- Maryk Yaml Definition: **Value<T>** T is name of model
- Kotlin Definition : **ValueModelDefinition<T>** T is for the name of DataModel
- Kotlin Value: **T** T stands for the data class which extends ValueDataModel 

## Usage options
- Value
- Map key
- Map value
- Inside List/Set

## Validation Options
- Required
- Final

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true
- dataModel - Refers to DataModel to be used as value

**Example of a kotlin ValueModel definition**
```kotlin
val def = ValueModelDefinition(
    required = false,
    final = true,
    dataModel = PersonRoleInPeriod
)
```

## Storage/Transport Byte representation
Each property of the value model is stored in its representative byte format. All 
values are combined into one array separated by a separator byte (0b0001) in the
order of how the properties are defined.

With transport the field is encoded as length delimited wire type preceded by length of bytes

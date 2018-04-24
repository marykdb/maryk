# Reference
Property representing a Reference to another DataObject.

- Maryk Yaml Definition: **Reference**
- Kotlin Definition : **ReferenceDefinition<T>** In which T is the DataModel name
- Kotlin Value : **Key<T>** In which T is the DataModel

## Usage options
- Value
- Map Key
- Map Value
- Inside List/Set

## Validation Options
- Required
- Final
- Unique
- Minimum value
- Maximum value

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true
- dataModel - Model of DataObjects to be referred to

**Example of a kotlin Reference definition**
```kotlin
val def = ReferenceDefinition(
    required = true,
    final = true,
    unique = true,
    dataModel = Person
)
```

## Storage/Transport Byte representation
The key of the referenced DataObject as bytes. With transport the Length Delimited
wiretype is used

## String representation
Key as base64 value

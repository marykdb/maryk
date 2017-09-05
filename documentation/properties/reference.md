# Reference
Property representing a Reference to another DataObject.

- Kotlin Definition : **ReferenceDefinition<T>** In which T is the DataModel name
- Maryk Yaml Definition: **Key<T>** In which T is the DataModel name

## Usage options
- Value
- Map Key
- Map Value
- List

## Validation Options
- Required
- Final
- Unique
- Minimum value
- Maximum value

## Data options
- dataModel - Model of DataObjects to be referred to
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true

**Example of a kotlin String definition**
```kotlin
ReferenceDefinition(
    name = "person",
    index = 0,
    required = true,
    final = true,
    unique = true,
    dataModel = Person
)
```

## Byte representation
The key of the referenced DataObject as bytes.

## String representation
Key as base64 value
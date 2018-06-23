# Value Property
A property which contains another DataModel as a value. See 
[DataModels](../../datamodel.md) for more details on how to define DataModels.

ValueDataModel objects are stored and transported as fixed length byte objects.
This makes them usable as map keys and list/set items.

- Maryk Yaml Definition: `ValueModel`
- Kotlin Definition: `ValueModelDefinition<T>` T is for the name of DataModel
- Kotlin Value: `T` T stands for the data class which extends ValueDataModel 

## Usage options
- Value
- Map key or value
- Inside List/Set

## Validation Options
- `required` - default true
- `final` - default false

## Other options
- `default` - the default value to be used if value was not set.
- `indexed` - default false
- `dataModel` - Refers to DataModel to be used as value

## Examples

**Example of a YAML ValueModel property definition**
```yaml
!Value
  dataModel: PersonRoleInPeriod
  required: false
  final: true
```

**Example of a Kotlin ValueModel property definition**
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

# Properties

Here we provide a comprehensive overview of how all [DataModels](../datamodel.md) define their structure
through properties. Each property is associated with a specific type,
including [Boolean](types/boolean.md), [Date](types/date.md), [String](types/string.md), and [Enum](types/enum.md).
Additionally, properties may include further details that determine their validation and storage mechanisms.

## Types of Properties

Here's a quick reference table that outlines the various types of properties and their characteristics:

| Type                                | Keyable | Indexable | List/Set | MapKey | MapValue | MultiType |
|:------------------------------------|:-------:|:---------:|:--------:|:------:|:--------:|:---------:|
| [String](types/string.md)           |    ❌    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Boolean](types/boolean.md)         |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Number](types/number.md)🔢         |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Enum](types/enum.md)               |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [GeoPoint](types/geopoint.md)       |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Date](types/date.md)               |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Time](types/time.md)               |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [DateTime](types/datetime.md)       |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Reference](types/reference.md)     |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [FixedBytes](types/fixedBytes.md)   |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [FlexBytes](types/flexBytes.md)     |    ❌    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [MultiType](types/multiType.md)     |   🆔    |     ✅     |    ❌     |   ❌    |    ✅     |     ✅     |
| [List](types/list.md)               |    ❌    |     ✅     |    ❌     |   ❌    |    ✅     |     ✅     |
| [Set](types/set.md)                 |    ❌    |     ✅     |    ❌     |   ❌    |    ✅     |     ✅     |
| [Map](types/map.md)                 |    ❌    |    🔑     |    ❌     |   ❌    |    ✅     |     ✅     |
| [IncMap](types/incrementingMap.md)  |    ❌    |    🔑     |    ❌     |   ❌    |    ✅     |     ✅     |
| [Embed](types/embeddedValues.md)    |    ❌    |    ⤵️     |    ❌     |   ❌    |    ✅     |     ✅     |
| [ValueObject](types/valueObject.md) |    ❌    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |

🔢 All numeric properties such as Int8/16/32/64, UInt8/16/32/64, Float, Double.  
🆔 Only the typeID of multitypes can be used in the key.  
🔑 Only the key of the map can be indexed.  
⤵️ Only specific values below the embedded object can be indexed, not the whole object itself.

### Property Characteristics:

- **Keyable**: Indicates if properties can be included in a key. (Only fixed byte length types can be used in key.) Key
  properties must be required and final.
- **MapKey**: Indicates if properties can serve as a key within a Map.
- **MapValue**: Indicates if properties can act as a value within a Map.
- **MultiType**: Indicates if properties can be included in a multi type.
- **List/Set**: Indicates if properties can be part of a List or Set.

## Operations

You can check or modify properties using operations like change, delete, or check. For detailed guidance, please refer
to our [operations documentation](operations.md).

## References

To filter, order, or report validation exceptions related to properties, you will need to make use of [
`property references`](references.md).

## Validation

All property definitions include methods for adding validations. These validations may involve making a property
required, specifying a minimum value or length, or applying specific validations such as regex for String properties.

Check the page dedicated to each property type to see the full range of validation options available.

### Common Validations for All Properties:

* **Required**: This property must be set. Default = true.
* **Final**: This property cannot be changed once set. Default = false.
* **Default**: Sets a default value. Default = null.

### Type-Specific Validations:

* **Unique**: Ensures no other field with this value exists, which is useful for unique external IDs and email
  addresses.
* **Minimum Value**: The property's value cannot be less than this specified value.
* **Maximum Value**: The property's value cannot exceed this specified value.
* **Minimum Size**: The property must not be smaller than this size.
* **Maximum Size**: The property must not be larger than this size.
* **Regular Expression**: For string properties, the content must match the specified regex.

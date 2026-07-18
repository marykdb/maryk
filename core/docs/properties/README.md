# Properties

Maryk models are built from properties. Each property declares the data type, how the value is validated and how it is stored. This page gives a high‑level overview of the available property types and common concepts for working with them.

## Property Type Characteristics

The table below lists the available property types and where they can be used.

| Type                                | Keyable | Indexable | List/Set | MapKey | MapValue | MultiType |
|:------------------------------------|:-------:|:---------:|:--------:|:------:|:--------:|:---------:|
| [String](types/string.md)           |    ❌    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Boolean](types/boolean.md)         |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Number](types/number.md)🔢         |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Decimal](types/decimal.md)         |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Enum](types/enum.md)               |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [GeoPoint](types/geopoint.md)       |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Date](types/date.md)               |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Time](types/time.md)               |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [DateTime](types/datetime.md)       |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [Reference](types/reference.md)     |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [FixedBytes](types/fixedBytes.md)   |    ✅    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [FlexBytes](types/flexBytes.md)     |    ❌    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |
| [MultiType](types/multiType.md)     |    🆔    |     ✅     |    ❌     |   ❌    |    ✅     |     ✅     |
| [List](types/list.md)               |    ❌    |     ❌     |    ❌     |   ❌    |    ✅     |     ✅     |
| [Set](types/set.md)                 |    ❌    |     ✅     |    ❌     |   ❌    |    ✅     |     ✅     |
| [Map](types/map.md)                 |    ❌    |    🔑     |    ❌     |   ❌    |    ✅     |     ✅     |
| [IncMap](types/incrementingMap.md)  |    ❌    |    🔑     |    ❌     |   ❌    |    ✅     |     ✅     |
| [Embed](types/embeddedValues.md)    |    ❌    |    ⤵️     |    ❌     |   ❌    |    ✅     |     ✅     |
| [ValueObject](types/valueObject.md) |    ❌    |     ✅     |    ✅     |   ✅    |    ✅     |     ✅     |

🔢 All numeric properties such as Int8/16/32/64, UInt8/16/32/64, Float, Double  
🆔 Only the type ID of multi types can be used in the key  
🔑 Only the map key can be indexed  
⤵️ Only nested values inside the embedded object can be indexed

## Choosing Number or Decimal

Use [Number](types/number.md) for values that fit a selected built-in integer
type, or for intentional `Float`/`Double` approximation. Use
[Decimal](types/decimal.md) for exact fixed-point domain values such as money,
rates, and measured quantities.

Decimal also supports exact whole numbers: set `scale = 0u` and choose the
required `byteSize` when an ordered, aggregatable integer can exceed `Long`.
For example, `byteSize = 16` stores a signed 128-bit integer. Choose the type
from domain semantics and range, not merely from whether the current examples
happen to have decimal places.

### Characteristic meanings

- **Keyable** – Property can participate in a key. Key properties must be required and final.
- **MapKey** – Property can be used as the key of a map.
- **MapValue** – Property can act as the value in a map.
- **MultiType** – Property can be part of a multi type.
- **List/Set** – Property can be stored inside a list or set.

## Operations

Use property operations such as change, delete or check to inspect or modify values. See the [operations](operations.md) page for details.

## References

[Property references](references.md) point to specific fields within a model and are used for filters and sorting.

## Validation

Every property definition supports validation helpers. Common options include:

* **required** – the property must be set (default `true`)
* **final** – value cannot change once set (default `false`)
* **default** – value to use if none is provided (default `null`)

Each type may define extra validations such as `minSize`, `maxSize`, ranges or regex patterns. Refer to the individual type pages for full details.

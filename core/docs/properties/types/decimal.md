# Decimal

Represents an exact fixed-scale decimal backed by an arbitrary-size signed
unscaled integer. Each property stores that integer in a configurable fixed
width of one through 128 bytes. Use it for money, rates, quantities, and
measurements where binary floating-point rounding is not acceptable.

- Kotlin Definition: `DecimalDefinition`
- Kotlin Value: `Decimal`
- Maryk YAML Definition: `Decimal`

`Decimal` is a storage and query value type with exact arbitrary-precision
arithmetic. A field's accepted range depends on its configured scale and
storage byte size.

## When to choose Decimal

Choose `Decimal` when the value must remain exact after storage, transport,
comparison, indexing, and aggregation. Typical cases are money, exchange or
tax rates, measured quantities, and quotas with a known smallest unit.

Use `NumberDefinition` instead when a built-in integer type has the required
range, or when approximate binary floating-point is intentional. `Decimal` is
not a replacement for arbitrary rounding rules: the property scale is fixed,
and Maryk never rounds a value implicitly.

`scale = 0u` is also useful. It makes Decimal an exact whole-number type with
a configurable signed range, for example a 128-bit inventory total, ledger
sequence, or externally defined numeric identifier that exceeds `Long`.
It still has Decimal's sortable fixed-width storage and aggregation behavior.

## Usage options

- Value
- Map key or value
- Inside List/Set
- Property or composite index part

## Value format and range

A value consists of:

- an arbitrary-size signed unscaled integer
- `scale`: the number of digits after the decimal point, from `0` through `18`
- `byteSize`: the signed storage width, from `1` through `128`, default `8`

For example, `Decimal.fromUnscaled(1230L, 2u)` represents `12.30`.

Create values from exact decimal text:

```kotlin
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.toDecimal

val amount = Decimal.parse("12.30")
val debit = Decimal.parse("-0.05")
val units = Decimal.fromUnscaled(1_250L, scale = 3u) // 1.250
val wholeUnits = 12.toDecimal(scale = 2u) // 12.00
val largeUnscaled = Decimal.fromUnscaled("123456789012345678901", scale = 2u)
```

`fromUnscaled(Long, scale)` and `unscaledValue` remain convenient for values
that fit in `Long`. `fromUnscaled(String, scale)` accepts an arbitrary-size
signed integer; use `parse` for ordinary decimal text. Kotlin's signed and
unsigned integer types provide `toDecimal(scale = 0u)` helpers. There is no
`Float` or `Double` helper: binary floating-point values are not exact decimal
inputs, so convert them through an explicit application rounding policy first.

Accepted text has an optional sign, at least one digit before the decimal
point, and optional fractional digits:

```text
0
-12
+42
12.30
-0.005
```

Scientific notation, `NaN`, infinity, `.5`, and `5.` are not accepted.

The unscaled value must fit in the property's configured `byteSize`. A signed
width of *n* bytes has the unscaled range
`-2^(8n-1)..2^(8n-1)-1`; divide that range by `10^scale` for the decimal
range. At scale `2`, a 2-byte property ranges from `-327.68..327.67`; the
default 8-byte property ranges from
`-92233720368547758.08..92233720368547758.07`.

## Whole-number Decimal

Set `scale = 0u` when a domain value has no fractional unit. The stored value
is an integer, while `byteSize` selects its signed capacity. This is distinct
from a `NumberDefinition`: Decimal can use up to 128 storage bytes and remains
the right choice when the value needs Decimal's exact transport and arithmetic
semantics.

```kotlin
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.decimal
import maryk.core.properties.types.toDecimal

object Inventory : RootDataModel<Inventory>() {
    val totalUnits by decimal(index = 1u, scale = 0u, byteSize = 16)
}

val initialUnits = 1_000_000.toDecimal()
```

Here `byteSize = 16` stores a signed 128-bit whole number. Use the smallest
width that covers the domain's required range; changing it later requires a
storage migration.

## Scale and exactness

The property `scale` is required. Maryk normalizes accepted values to that
scale without rounding:

```kotlin
Decimal.parse("12.3").rescaleExact(2u)   // 12.30
Decimal.parse("12.30").rescaleExact(1u)  // 12.3
Decimal.parse("12.34").rescaleExact(1u)  // throws: precision would be lost
```

Increasing the scale is exact and does not impose a `Long` limit. It can still
fail when writing through a definition whose configured `byteSize` is too
small for the resulting unscaled value.

Numeric comparison works exactly across scales, so `12.3` and `12.300` compare
as equal. Object equality remains representation-sensitive: those two values
have different scales and are therefore not equal as Kotlin objects. Values
read through one `DecimalDefinition` are normalized to its declared scale.

## Validation Options

- `required` - default `true`
- `final` - default `false`
- `unique` - default `false`
- `minValue` - optional inclusive minimum
- `maxValue` - optional inclusive maximum

`minValue`, `maxValue`, and `default` must be exactly representable at the
property scale and storage byte size. The minimum cannot be greater than the
maximum.

## Other options

- `scale` - required number of fractional digits, `0..18`
- `byteSize` - fixed signed storage width, `1..128`, default `8`
- `default` - value used when the property was not set
- `reversedStorage` - reverses byte ordering for descending index storage
- `sensitive` - marks the model property as sensitive
- `name` - overrides the serialized property name
- `alternativeNames` - accepted legacy names during schema evolution

## Examples

**Example of a Decimal property definition for use within a Model**

```kotlin
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.decimal
import maryk.core.properties.types.Decimal

object Invoice : RootDataModel<Invoice>() {
    val amount by decimal(
        index = 1u,
        scale = 2u,
        byteSize = 8,
        required = true,
        final = false,
        unique = false,
        minValue = Decimal.parse("0.00"),
        maxValue = Decimal.parse("999999999.99"),
        default = Decimal.parse("12.30"),
    )
}
```

Values with fewer fractional digits are normalized:

```kotlin
val invoice = Invoice.create {
    amount with Decimal.parse("10.5") // stored and transported as 10.50
}
```

**Example of a separate Decimal property definition**

```kotlin
import maryk.core.properties.definitions.DecimalDefinition

val definition = DecimalDefinition(
    scale = 4u,
    byteSize = 4,
    required = true,
    final = true,
    unique = false,
    minValue = Decimal.parse("-100.0000"),
    maxValue = Decimal.parse("100.0000"),
    default = Decimal.parse("0.0000"),
)
```

## Filtering and indexing

Decimal supports the normal comparable-value filters, including equality,
less/greater-than, range, and value-in filters. Its fixed-width storage bytes
sort in numeric order, so it can be used directly in property and composite
indexes.

Use values that match the property's logical scale in application code. The
definition also normalizes filter and transport strings when it parses them.

## Arithmetic aggregations

`DecimalDefinition` implements the same arithmetic capability as primitive
number definitions. Decimal properties therefore support `Sum`, `Average`, and
`Stats`.

- Addition, subtraction, multiplication, and exact division are available on `Decimal` values.
- Definition arithmetic fails when its configured storage width would overflow.
- Sum results retain the property scale.
- Average results retain the property scale.
- A non-exact average uses round-half-to-even at that scale.

For example, at scale `2`, an exact average of `1.515` is returned as `1.52`,
while `1.505` is returned as `1.50`. Parsing, validation, storage, and explicit
rescaling remain exact and never round implicitly.

## Storage Byte representation

Decimal uses the property's `byteSize` (default `8`, maximum `128`) storage
bytes. Maryk encodes the normalized signed arbitrary-size unscaled value in
sortable form:

- lexicographic byte order matches numeric order;
- negative values sort before zero and positive values;
- scale is not stored per value because it belongs to the property definition;
- `reversedStorage = true` complements the bytes to reverse their order.

Changing the property scale or byte size changes stored values and index keys.

## Transport Byte representation

Transport uses the ProtoBuf `LENGTH_DELIMITED` wire type containing the UTF-8
decimal string at the property's fixed scale. JSON and YAML also write the
value as a string. Generated Proto3 schemas therefore use `string`:

```proto
message Invoice {
  string amount = 1;
}
```

This avoids binary floating-point conversion and preserves trailing fractional
zeros across Kotlin, JavaScript, ProtoBuf, JSON, and YAML.

## Schema evolution

Changing `scale`, `byteSize`, or `reversedStorage` is storage-incompatible and
requires a data migration. Additive property changes and compatible validation
changes follow the normal Maryk schema compatibility rules.

When changing scale, migrate by reading with the old definition, applying an
explicit rescaling policy, and writing with the new definition. Maryk does not
choose a rounding rule implicitly.

## String representation

Plain fixed-point decimal text with exactly the value's scale, for example
`12.30`, `0`, or `-0.005`.

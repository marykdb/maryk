# Property References

To filter on a property you need a property reference. For example, to find all people named `Smith` you need a reference to the `firstName` property. Other databases might call this a column. Any [`DataModel`](../datamodel.md) can supply these references.

These operations can be defined in Kotlin or any of the serialization formats Maryk supports. 

## Example models

This `Person` model has two top level fields (`firstName`, `lastName`) and an embedded `Address`:
```kotlin
object Person : RootDataModel<Person>() {
    val firstName by string(index = 1u)
    val lastName by string(index = 2u)
    val livingAddress by embed(
        index = 3u,
        dataModel = { Address }
    )
}
```

```kotlin
object Address : DataModel<Address>() {
  val street by string(index = 1u)
  val city by string(index = 2u)
}
```

## Creating property references

With compiled models Kotlin produces type strict property definitions. This means your IDE will
help to validate and autocomplete them.

`RootDataModel`, `DataModel`, and `ObjectDataModel` inherit `ref`. Return the property you want; no
helper import is needed:

```kotlin
// Reference to firstName property
Person.ref { firstName }

// Reference to street through a Person
Person.ref { livingAddress { street } }
```

Each block retains its concrete model receiver, so the IDE can suggest Person
properties in the outer block and Address properties in the embedded block.
The selected property's value type is preserved for typed query operands:

```kotlin
Equals(Person.ref { firstName } with "Jane")
```

This syntax uses the existing model receivers; it does not hide their other
public members from completion or require generated scope classes.

For deeper embedding, chain blocks and return the leaf property:

```kotlin
Model.ref { property { property { property { leaf } } } }
```

Direct selection preserves the property's concrete reference type, including
through embedded models, map navigation, referenced models, and multi-type
branches. It supports index definitions, reference-specific change operations,
and storage-byte operations without casts or an inner `::ref`.

```kotlin
val index: IsIndexable = Person.ref { livingAddress { street } }
val change = Model.ref { incMap }.change(addValues = listOf("new"))
```

Property wrappers expose this relationship through `IsReferenceCreator<R>`.
Custom wrappers can implement that interface using their existing concrete
`ref(parentRef)` override. Generic helpers should retain this contract when
they need to preserve the concrete return type.

The original callable-reference selectors remain supported for compatibility.
The outer `Person { ... }` reference syntax is deprecated in favor of `Person.ref`.

```kotlin
Person.ref { firstName }
Person.ref { livingAddress { street } }
```

For generic code whose model is typed only as an `IsDataModel` interface, and for
framework models based directly on `TypedObjectDataModel` (including
`DefinitionModel`), the extension remains available through
`import maryk.core.properties.references.dsl.ref`.
Existing imports can remain; concrete models use their inherited member.

Embedded values and embedded objects support direct property selection. Map
`at(key)` and `any { ... }`, referenced-model blocks, and multi-type `withType`
and `atType` blocks also support returning the child property directly.

## Creating property references with String notation

When defined in YAML or JSON, property references can use a simple string format without including the model name.

To refer to the firstName on Person you use `firstName` and for the street on Address within Person you 
use `livingAddress.street`

## Referring to values of maps

It is also possible to refer to a value inside a map to filter or order:

```kotlin
// Model contains map with Time as a key
// Refer to the value at 12:23
Model.ref { map.at(Time(12, 23)) }
```

In string notation this becomes `map.@12:23`. References to the key itself use `map.#12:23`.

### Wildcards for maps

Maps support wildcard references for both values and keys:

```kotlin
// Any key in the map
Model.ref { map.anyKey() }

// Any value in the map
Model.ref { map.anyValue() }
```

String notation:
- `map.~` = any map key
- `map.*` = any map value

## Referring to specific values in sets

Items in sets can be selected by value
```kotlin
// Model with a set property of Time
// Refer to the value at Time(12, 23)
Model.ref { set.item(Time(12, 23)) }
```

In string notation this is `set.#12:23`.

### Wildcards for sets

Sets support wildcard references to match any item value:

```kotlin
Model.ref { set.any() }
```

String notation:
- `set.*` = any set value

## Referring to index of lists

Items in lists can also be selected by index
```kotlin
// Model with list property
// Refer to the value at index 5
Model.ref { list.at(5u) }
```

In string notation this is `list.@5`.

`list.@<index>` and `list.*` references are usable for filtering and changes.
They are not valid for `RootDataModel` index definitions.

# Multi Type Property
A property that can contain a value of one of multiple predefined types. Each type has its own property definition. Refer to [properties](../README.md) for supported definitions.

This multi type property could be useful to use different DataModels inside one field. 
For example in a Timeline in which multiple types of data like posts, photos, videos
events and more are shown, it is useful to store everything in one RootDataModel. This way it 
is easy to query all the time ordered data while they each can have different data structures. 
It is also easy to use other types of structures like single values, maps, sets or even deeper 
multi types.

It is also possible to store the type in the key or index with a Type Reference. This way it will be
cheaper to query on type.

- Kotlin Definition: `MultiTypeDefinition`
- Kotlin Value: `TypedValue`
- Maryk Yaml Definition: `MultiType`

## Usage options
- Value

## Validation Options
- `required` - default true
- `final` - default false

## Other options
- `default` - the default value to be used if value was not set.
- `typeIsFinal` - default true. Set to true to make type non changable once set.
- `typeEnum` - `MultiTypeEnumDefinition` which maps ids to definitions

## Examples

**Example of a separately defined Multi type enum**
This example is useful if the multi type definition is used in multiple locations.

**Example of a Multi type enum**
```kotlin
sealed class MultiType<T: Any>(
    index: UInt,
    override val definition: IsUsableInMultiType<T, IsPropertyContext>?
) : IndexedEnumImpl<MultiType<*>>(index), MultiTypeEnum<T> {
    object S1: MultiType<String>(1u, StringDefinition())
    object S2: MultiType<Short>(2u, NumberDefinition(type = SInt16))
    object S3: MultiType<Values<EmbeddedModel>>(3u, EmbeddedValuesDefinition(dataModel = { EmbeddedModel }))

    class UnknownMultiType(index: UInt, override val name: String): MultiType<Any>(index, null)

    companion object : MultiTypeEnumDefinition<MultiType<out Any>>(
        MultiType::class,
        values = { arrayOf(S1, S2, S3) },
        unknownCreator = ::UnknownMultiType
    )
}
```

**Example of a multi type property definition for use within a Model**
```kotlin
val category by multiType(
    index = 1u,
    required = false,
    final = true,
    typeEnum = MultiType,
    default = MultiType.S1("default")
)
```

**Example of a separate Enum property definition**
```kotlin
val def = MultiTypeDefinition(
    required = false,
    final = true,
    typeEnum = MultiType,
    default = MultiType.S1("a value")
)
```

## Setting Values (invoke DSL)
Multi-type fields are assigned using a typed wrapper (`TypedValue`). To keep code concise, enums that implement `MultiTypeEnum` support an invoke DSL.

- Regular (non-embedded) type values: call the enum option like a function with the value.
- Embedded values: build the embedded `Values` via a builder lambda on the enum option.

The snippets below illustrate both styles using a sample enum `SimpleMultiType` with:
- `S1: String`
- `S3: Values<EmbeddedModel>`

```kotlin
// Property on a DataModel
val multi by multiType(
    index = 1u,
    required = false,
    typeEnum = SimpleMultiType,
)

// 1) Setting a regular value
val v1 = Values(YourModel) {
    multi with S1("value")
}

// 2) Setting an embedded value
// Add: import maryk.core.properties.enum.invoke
val v2 = Values(YourModel) {
    multi with S3 {
        value with "v1"
    }
}

```

## Storage Byte representation
Depends on the specific implementation. The type id is stored as UInt16 and the value in its
native form.

## Transport Byte representation
The multitype is encoded as an embedded object within a length delimited tag/value. It then 
contains 1 tag/value pair in which the tag is the type index and the value contains the actual value. 

## String representation
TypeID as a `UInt16` encoded to string.

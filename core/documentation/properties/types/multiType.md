# Multi Type Property
A property which can contain one of multiple types. The type is codified in an UInt16 value.

This property type is particularly useful to implement different tastes of the same DataModel. 
Imagine you want to create a Timeline in which multiple types of data like posts, photos, videos
events and more are shown. You want to have them all stored together in one RootDataModel so it 
is easy to query an ordered timeline but they have each different data requirements. For the post
it is about the text and the event about the date. For this a multi type property is useful so you
can store submodels of the different types in the same property.

It is also possible to store the type in the key so you can quickly filter all the events, posts
or photos. 

- Maryk Yaml Definition: `MultiType`
- Kotlin Definition: `MultiTypeDefinition`
- Kotlin Value: `TypedValue`

## Usage options
- Value

## Validation Options
- `required` - default true
- `final` - default false
- `unique` - default false

## Other options
- `default` - the default value to be used if value was not set.
- `indexed` - default false
- `searchable` - default true
- `definitionMap` - Map which maps shorts to a property definition

## Examples

**Example of a Kotlin Multi type property definition**
```kotlin
val intDef = NumberDefinition<Int>(
    name = "int",
    type = Int32
)

val stringDef = StringDefinition(
    name = "string"
)

enum class MultiType(
    override val index: Int
): IndexedEnum<Option> {
    ByString(0), ByInt(1)
}

val def = MultiTypeDefinition(
    required = true,
    final = true,
    unique = true,
    typeMap = mapOf(
        MultiType.ByString to StringDefinition(),
        MultiType.ByInt to NumberDefinition(type = Int32)
    )
)
```

**Example of a YAML MultiType property definition**
```yaml
!MultiType
  required: false
  final: true
  definitionMap:
  ? 0: ByString
  : !String
  ? 1: ByInt
  : !Number
    type: SINT32
```

## Storage Byte representation
Depends on the specific implementation. The type id is stored as UInt16 and the value in its
native form.

## Transport Byte representation
The multitype is encoded as an embedded object within a length delimited tag/value. It then 
contains 2 tag/value pairs with the first one with `tag=1` being the type index encoded in VarInt and 
secondly a `tag=2` with the value itself. 

## String representation
TypeID as a `UInt16` encoded to string.

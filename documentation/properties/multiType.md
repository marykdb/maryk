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

- Maryk Yaml Definition: **MultiType**
- Kotlin Definition : **MultiTypeDefinition**
- Kotlin Value: **TypedValue**

## Usage options
- Value

## Validation Options
- Required
- Final
- Unique

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true
- typeMap - Map which maps shorts to a property definition

**Example of a kotlin Multi type definition**
```kotlin
val intDef = NumberDefinition<Int>(
    name = "int",
    type = Int32
)

val stringDef = StringDefinition(
    name = "string"
)

val def = MultiTypeDefinition(
    name = "logItem",
    index = 0,
    required = true,
    final = true,
    unique = true,
    typeMap = mapOf(
            0 to stringDef,
            1 to intDef
    )
)
```

## Storage Byte representation
Depends on the specific implementation. The type id is stored as UInt16 and the value in its
native form.

## Transport Byte representation
The multitype is encoded as an embedded object within a length delimited tag/value. It then 
contains 2 tag/value pairs with the first one with tag=1 being the type index encoded in VarInt and 
secondly a tag=2 with the value itself. 

## String representation
TypeID as a UInt16 encoded to string.
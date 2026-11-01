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
        values = { listOf(S1, S2, S3) },
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

**Example of a separate MultiType property definition**
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

## Design recipes

Example — contact variants on a user:
```kotlin
object EmailContact : DataModel<EmailContact>() {
    val address by string(index = 1u)
    val verified by boolean(index = 2u)
}

object PhoneContact : DataModel<PhoneContact>() {
    val number by string(index = 1u)
    val country by string(index = 2u)
}

// Define a MultiType enum for contact variants
sealed class ContactMT<T: Any>(index: UInt, override val definition: IsUsableInMultiType<T, IsPropertyContext>?) : IndexedEnumImpl<ContactMT<*>>(index), MultiTypeEnum<T> {
    object Email: ContactMT<Values<EmailContact>>(1u, EmbeddedValuesDefinition(dataModel = { EmailContact }))
    object Phone: ContactMT<Values<PhoneContact>>(2u, EmbeddedValuesDefinition(dataModel = { PhoneContact }))

    companion object : MultiTypeEnumDefinition<ContactMT<out Any>>(ContactMT::class, values = { listOf(Email, Phone) })
}

object User : RootDataModel<User>() {
    val name by string(index = 1u)
    val contact by multiType(index = 2u, typeEnum = ContactMT)
}
```

Read only fields for the email variant:
```kotlin
val g = User.graph {
    listOf(name, contact.withTypeGraph(ContactMT.Email) { listOf(address, verified) })
}
```

Polymorphic collections with MultiType:
```kotlin
object ImageAttachment : DataModel<ImageAttachment>() {
    val url by string(index = 1u)
    val width by number(index = 2u, type = SInt32)
}

object FileAttachment : DataModel<FileAttachment>() {
    val name by string(index = 1u)
    val bytes by flexBytes(index = 2u)
}

// MultiType enum for attachments
sealed class AttachmentMT<T: Any>(index: UInt, override val definition: IsUsableInMultiType<T, IsPropertyContext>?) : IndexedEnumImpl<AttachmentMT<*>>(index), MultiTypeEnum<T> {
    object Image: AttachmentMT<Values<ImageAttachment>>(1u, EmbeddedValuesDefinition(dataModel = { ImageAttachment }))
    object File: AttachmentMT<Values<FileAttachment>>(2u, EmbeddedValuesDefinition(dataModel = { FileAttachment }))

    companion object : MultiTypeEnumDefinition<AttachmentMT<out Any>>(AttachmentMT::class, values = { listOf(Image, File) })
}

object Message : RootDataModel<Message>() {
    val text by string(index = 1u)
    val attachments by map(
        index = 2u,
        keyDefinition = NumberDefinition(type = UInt32),
        valueDefinition = MultiTypeDefinition(typeEnum = AttachmentMT),
    )
}

// Fetch the bounded attachment map; its keys identify individual attachments.
val g = Message.graph { listOf(text, attachments) }
```

Use a keyed map for structured or multi-type collection entries; public list/set
elements must have a collection-compatible scalar or fixed-value definition.

MultiType with keys: cluster by variant for fast scans using `Model.ref { property.type }` in the key. This keeps same‑type values together on disk.
```kotlin
object Activity : RootDataModel<Activity>(
    keyDefinition = { Multiple(Activity.user.ref(), Reversed(Activity.timestamp.ref()), Activity.ref { item.type }) }
) {
    val user by reference(index = 1u, dataModel = { User }, final = true)
    val timestamp by dateTime(index = 2u, final = true)
    val item by multiType(index = 3u, typeEnum = ContactMT)
}
```

## Storage Byte representation
Depends on the specific implementation. The type id is stored as UInt16 and the value in its
native form.

## Transport Byte representation
The multitype is encoded as an embedded object within a length delimited tag/value. It then 
contains 1 tag/value pair in which the tag is the type index and the value contains the actual value. 

Repeated occurrences of the same case can accumulate collection entries.
Changing the case starts a new payload; entries from the preceding case do not
carry over. Contextual definitions preserve the same continuation rules.

In JSON, a typed value is an array containing exactly its type name and payload,
for example `["S1", "a value"]`. Extra elements are rejected.

## String representation
The string form is the JSON representation of the complete typed value, for
example `["S1", "a value"]`. A type reference identifies only the case.

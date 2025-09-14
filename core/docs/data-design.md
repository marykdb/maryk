# Designing Data with Maryk

This guide helps you design data models that read and write efficiently in Maryk. You’ll learn how to decide when to embed data, when to split into separate models with references, how to use MultiType for polymorphic shapes, how keys influence locality, and how graphs let you fetch only what you need.

What you’ll learn
- A simple mental model for Maryk data.
- How to choose between embedding and referencing.
- When to reach for MultiType or ValueDataModel.
- How keys shape query performance.
- How graphs keep payloads small.

## 1) The mental model

- DataModel: the schema for a record, with named properties that each have a stable `UInt` index for fast, reflection‑free serialization. See [Data Models](datamodel.md).
- RootDataModel: a top‑level model that can be stored and queried. Roots define keys and indexes. See [Keys](key.md).
- Properties: rich types including embedded objects, lists/sets/maps, references, and [MultiType](properties/types/multiType.md).
- Queries: filter using property references and select fields with reference graphs. See [Property References](properties/references.md) and [Reference Graphs](reference-graphs.md).
- Versioning: evolve safely with built‑in schema/data versioning. See [Versioning](versioning.md).

Maryk rewards colocating related data, then using graphs to fetch only what you need.

## 2) Query only what you need (graphs)

Embedding is practical because queries don’t have to return whole objects. A reference graph describes exactly which properties to read, including nested fields and type‑specific `multiType` selections.

Example — select a user’s `name` and only the `city` inside an embedded `address`:
```kotlin
val g = User.graph {
    listOf(
        name,
        graph(address) { listOf(city) }
    )
}
```
Keep this in mind as you make design choices below.

## 3) Embed or reference? A quick decision guide

Embed (keep together) when:
- You usually read/write the fields together.
- The nested collection is small or bounded.
- The nested values share the parent’s lifecycle and permissions.
- You want atomic updates across parent + nested fields.

Split into a separate root + reference when:
- The child collection can grow large/unbounded.
- The child needs its own lifecycle, permissions, or global queries.
- You anticipate independent write contention on the child.

Tip: Even when you embed, use graphs to keep reads lean.

## 4) Embedding in practice

```kotlin
object Address : DataModel<Address>() {
    val street by string(index = 1u)
    val city by string(index = 2u)
    val postalCode by string(index = 3u)
}

object User : RootDataModel<User>() {
    val name by string(index = 1u)
    val address by embeddedObject(index = 2u, dataModel = { Address })
    val tags by set(index = 3u, valueDefinition = StringDefinition())
}
```

Why this works well
- One round‑trip to read the user and address.
- Atomic updates and shared versioning.
- With graphs, you don’t over‑fetch.

Graph example — pick only what matters:
```kotlin
val g = User.graph {
    listOf(name, graph(address) { listOf(city) })
}
```

## 5) MultiType: one property, several shapes

`multiType` is a typed union. A single property can hold one of several well‑defined variants; each variant has its own schema. Use it when the shape varies across a small set of alternatives and you want type‑safe selection at read time.

When to use
- Variant depends on context (Email vs Phone contact).
- Keep related data embedded while allowing shape differences.
- Read variant‑specific fields via graphs.

When to avoid
- Many variants that must be queried globally as first‑class objects → separate roots.
- Unbounded/dynamic variants that need global indexing per type.

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
    object Email: ContactMT<Values<EmailContact>>(1u, EmbeddedObjectDefinition(dataModel = { EmailContact }))
    object Phone: ContactMT<Values<PhoneContact>>(2u, EmbeddedObjectDefinition(dataModel = { PhoneContact }))

    companion object : MultiTypeEnumDefinition<ContactMT<out Any>>(ContactMT::class, values = { arrayOf(Email, Phone) })
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
    val width by number(index = 2u, type = Int32)
}

object FileAttachment : DataModel<FileAttachment>() {
    val name by string(index = 1u)
    val bytes by flexBytes(index = 2u)
}

// MultiType enum for attachments
sealed class AttachmentMT<T: Any>(index: UInt, override val definition: IsUsableInMultiType<T, IsPropertyContext>?) : IndexedEnumImpl<AttachmentMT<*>>(index), MultiTypeEnum<T> {
    object Image: AttachmentMT<Values<ImageAttachment>>(1u, EmbeddedObjectDefinition(dataModel = { ImageAttachment }))
    object File: AttachmentMT<Values<FileAttachment>>(2u, EmbeddedObjectDefinition(dataModel = { FileAttachment }))

    companion object : MultiTypeEnumDefinition<AttachmentMT<out Any>>(AttachmentMT::class, values = { arrayOf(Image, File) })
}

object Message : RootDataModel<Message>() {
    val text by string(index = 1u)
    val attachments by list(
        index = 2u,
        valueDefinition = MultiTypeDefinition(typeEnum = AttachmentMT)
    )
}

// Select only image URLs from attachments
val g = Message.graph {
    listOf(graph(attachments) { withTypeGraph(AttachmentMT.Image) { listOf(url) } })
}
```

MultiType with keys: cluster by variant for fast scans using `TypeId(property)` in the key. This keeps same‑type values together on disk.
```kotlin
object Activity : RootDataModel<Activity>(
    keyDefinition = { Multiple(user.ref(), Reversed(timestamp.ref()), TypeId(item)) }
) {
    val user by reference(index = 1u, dataModel = { User })
    val timestamp by dateTime(index = 2u)
    val item by multiType(index = 3u, typeEnum = ContactMT)
}
```

See the full [MultiType](properties/types/multiType.md) reference for API details.

## 6) ValueDataModel vs embedded objects

Use `ValueDataModel` when the composite can be represented in fixed bytes and you need it as a compact value, map key, or index component. Use `embeddedObject` for richer, variable‑sized structures.

- ValueDataModel: great for ranges, periods, coordinates, composite keys.
- Embedded object: great for readable sub‑records and larger nested data.
- MultiType: great for compact polymorphic sub‑records.

## 7) Splitting into roots: references and indexes

When collections grow or the child needs independence, split into a root model and relate via a `reference`.

Start embedded; split when needed — Orders and OrderLines:
```kotlin
object OrderLine : DataModel<OrderLine>() {
    val sku by string(index = 1u)
    val quantity by number(index = 2u, type = Int32)
    val priceCents by number(index = 3u, type = Int32)
}

object Order : RootDataModel<Order>() {
    val orderId by string(index = 1u)
    val customerId by string(index = 2u)
    val lines by list(index = 3u, valueDefinition = EmbeddedObjectDefinition(dataModel = { OrderLine }))
}

// Split version
object Order : RootDataModel<Order>(keyDefinition = { StringKey(orderId.ref()) }) {
    val orderId by string(index = 1u)
    val customerId by string(index = 2u)
}

object OrderLine : RootDataModel<OrderLine>(
    keyDefinition = { Multiple(reference(Order) { order.ref() }, Incrementing()) },
    indexes = listOf(Multiple(sku.ref()))
) {
    val order by reference(index = 1u, dataModel = { Order })
    val lineNo by number(index = 2u, type = Int32)
    val sku by string(index = 3u)
    val quantity by number(index = 4u, type = Int32)
    val priceCents by number(index = 5u, type = Int32)
}
```

Guidelines
- Design composite keys for your dominant scans (by parent, by time, etc.).
- Add indexes only for real secondary lookups (e.g., by `sku`).
- Keep reads efficient using graphs; for enrichment across models, see [Collect & Inject](collectAndInject.md).

## 8) Keys and locality patterns

Keys decide how data is clustered and thus how range scans perform.

Common patterns
- Owner + reversed timestamp: `Multiple(user.ref(), Reversed(date.ref()))` → latest first per user.
- Include `TypeId(property)` to cluster polymorphic values by variant.

Feed example:
```kotlin
object FeedItem : RootDataModel<FeedItem>(
    keyDefinition = { Multiple(user.ref(), Reversed(postedAt.ref()), TypeId(content)) }
) {
    val user by reference(index = 1u, dataModel = { User })
    val postedAt by dateTime(index = 2u)
    // MultiType enum for feed content
    val content by multiType(index = 3u, typeEnum = FeedContentMT)
}

// Define the MultiType enum used in FeedItem
sealed class FeedContentMT<T: Any>(index: UInt, override val definition: IsUsableInMultiType<T, IsPropertyContext>?) : IndexedEnumImpl<FeedContentMT<*>>(index), MultiTypeEnum<T> {
    object PostType: FeedContentMT<Values<Post>>(1u, EmbeddedObjectDefinition(dataModel = { Post }))
    object EventType: FeedContentMT<Values<Event>>(2u, EmbeddedObjectDefinition(dataModel = { Event }))

    companion object : MultiTypeEnumDefinition<FeedContentMT<out Any>>(FeedContentMT::class, values = { arrayOf(PostType, EventType) })
}
```

## 9) Denormalize thoughtfully

Keep an authoritative source and copy a few stable, display‑oriented fields to avoid joins during reads. Pair with a `reference` back to the source.

```kotlin
object Product : RootDataModel<Product>() {
    val id by string(index = 1u)
    val name by string(index = 2u)
    val priceCents by number(index = 3u, type = Int32)
}

object OrderLine : RootDataModel<OrderLine>() {
    val product by reference(index = 1u, dataModel = { Product })
    val productName by string(index = 2u) // snapshot for display
    val productPriceCents by number(index = 3u, type = Int32)
}
```

Prefer denormalizing stable fields (names, slugs). Avoid duplicating highly volatile fields unless you control updates.

## 10) Evolve safely

- Start embedded for simplicity; split into roots as scale/query needs grow.
- Use [Versioning](versioning.md) to add properties and indexes, backfill, then remove obsolete fields.
- When splitting, keep both paths during migration; write to both until backfill completes.

## Design checklist

- What is the dominant read path? Design keys for it.
- Do I always read this nested data with the parent? If yes, embed; otherwise consider a separate root.
- Could the collection grow unbounded? If yes, separate root.
- Do variants fit a small, known set? Consider MultiType.
- What secondary lookups matter? Add only those indexes.
- Can I shrink payloads with graphs? Always define select graphs for hot paths.

Further reading
- [Data Models](datamodel.md)
- [Property References](properties/references.md)
- [Reference Graphs](reference-graphs.md)
- [Keys](key.md)
- [Versioning](versioning.md)

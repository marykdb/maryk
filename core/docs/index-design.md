# Index Design

This guide explains how to think about indexes in Maryk and how the different index building blocks fit together.

The main design choice is not "which operator should I use first?". The main design choice is:

- do I need an ordered index
- do I need a search index
- or do I need both

That distinction matters because those two index families solve different problems and should not be mixed mentally.

Related guides:
- [Data Models](datamodel.md)
- [Keys](key.md)
- [Filters](filters.md)
- [Querying](query.md)

## Two index families

### Ordered indexes

An ordered index produces one index key per record.

That key has a stable byte order. Because of that, ordered indexes are the right tool for:

- sorting
- exact lookup
- prefix scans
- range scans
- pagination over a stable order

If you think in terms of "sort by this, then by that" or "scan all values between A and B", you want an ordered index.

### Search indexes

A search index can produce multiple index entries per record.

That makes it useful for text-like search where one stored field should be searched as multiple terms, or where several fields should behave like one search surface.

Search indexes are the right tool for:

- token matching
- search across multiple fields
- normalized name search
- query-box style lookup over several name/address/alias parts

Search indexes are not ordering indexes. They are for matching, not for stable record order.

### Why the distinction matters

Suppose you need both:

- sort people by surname, then first name
- search people by any name token

Those are different access patterns.

The ordered version wants something like:

```kotlin
Multiple(
    Normalize(surname.ref()),
    firstName.ref()
)
```

The search version wants something like:

```kotlin
AnyOf(
    "name",
    surname.ref(),
    firstName.ref(),
    fullName.ref()
).normalize().split(WordBoundary)
```

One record order. One search surface. Different indexes.

If you try to use one for the other, semantics become confusing quickly.

## Ordered index building blocks

These building blocks all belong to the ordered-index family.

### Reference

```kotlin
surname.ref()
```

This is the simplest ordered index part.

It stores the property value as-is in index order. Use it when exact stored value semantics matter.

Good for:
- exact equality
- normal prefix matching
- range scans
- straightforward ordering

### `Normalize`

```kotlin
Normalize(surname.ref())
```

`Normalize` is still an ordered index part. It does not make the index a search index by itself.

It changes the stored index value before it is written. For strings this currently means search-oriented normalization such as:

- lowercase folding
- diacritic folding
- punctuation and symbol cleanup
- whitespace normalization/removal

Use it when you want ordered lookups and scans to happen on normalized text instead of original text.

Good for:
- case-insensitive equality on an ordered index
- accent-insensitive prefix/range scans
- stable normalized sort order

Do not confuse this with token search. `Normalize` changes one value into one normalized value.

### `Reversed`

```kotlin
Reversed(createdAt.ref())
```

`Reversed` flips the ordering direction for one ordered index part.

Use it when the natural query pattern is descending, for example:

- newest first
- highest score first
- most recent version first

It is still an ordered index. It just changes the byte ordering for that part.

### `ReferenceToMax`

```kotlin
ReferenceToMax(updatedAt.ref())
```

`ReferenceToMax` is an ordered index helper for cases where you want a reference to sort toward the maximum end of the key space.

Use it when you need special ordering behavior around max-oriented positioning of a reference value inside an ordered key.

It belongs with ordered index composition, not search.

### `Multiple`

```kotlin
Multiple(
    Normalize(surname.ref()),
    firstName.ref()
)
```

`Multiple` combines several ordered index parts into one tuple index.

This is the main tool for compound ordered indexes.

Good for:
- order by A then B
- exact lookup on leading parts
- prefix scans on leading parts
- range scans over a tuple

Important:
- `Multiple` still produces one index key per record
- part order matters
- leading parts decide which scans can use the index efficiently

## Search index building blocks

These building blocks are different. They are about emitting search values, not about stable sort order.

### `Split`

```kotlin
Split(Normalize(fullName.ref()), on = WordBoundary)
```

`Split` turns one string into multiple indexed values.

That one step changes the nature of the index part: this is now search-oriented fan-out behavior.

Current split options:
- `SplitOn.Whitespace`
- `SplitOn.WordBoundary`

`SplitOn.Whitespace` only breaks on whitespace.

`SplitOn.WordBoundary` is broader. It breaks on whitespace and common hyphen boundaries. That makes it better for names such as:

- `García-López`
- `Jean-Luc`
- `van der Waals`

Use `Split` when one stored field should be searchable as multiple terms.

### `AnyOf`

```kotlin
AnyOf(
    "name",
    surname.ref(),
    firstName.ref(),
    fullName.ref()
)
```

`AnyOf` combines several sources into one logical search index.

Each child can emit one or more search values. The result is a single named search surface backed by several fields.

Use it when the user thinks of several fields as one search box.

Typical cases:
- patient name search across family/given/text/prefix/suffix
- address search across street/city/postcode
- alias search across several alternate names

If `AnyOf` has a name, it is queried through the named search filters:

- `Matches`
- `MatchesPrefix`
- `MatchesRegEx`

Important:
- `AnyOf` is search-only semantics
- named search index names should be unique per model
- it should not be treated as a replacement for ordered tuple indexes

## Composition patterns

### Ordered composition

Use ordered building blocks together when the query model is still about record order or exact/prefix/range semantics.

Example:

```kotlin
Multiple(
    Normalize(surname.ref()),
    firstName.ref(),
    Reversed(createdAt.ref())
)
```

Read that as:

- compare surname in normalized order
- then compare first name in stored order
- then compare created time in reverse order

Still one index key. Still an ordered index.

### Search composition

Use search building blocks when the query model is about terms instead of full field values.

Example:

```kotlin
AnyOf(
    "name",
    surname.ref(),
    firstName.ref(),
    fullName.ref()
).normalize().split(WordBoundary)
```

Read that as:

- take all these fields
- normalize each field
- split each field into terms
- index those emitted terms under one named search surface

This is no longer a sort index. It is a search index.

### Composable syntax

These forms are equivalent:

```kotlin
AnyOf(
    Split(Normalize(surname.ref()), on = WordBoundary),
    Split(Normalize(firstName.ref()), on = WordBoundary),
)
```

```kotlin
AnyOf(
    surname.ref().normalize().split(WordBoundary),
    firstName.ref().normalize().split(WordBoundary),
)
```

```kotlin
AnyOf(
    surname.ref(),
    firstName.ref(),
).normalize().split(WordBoundary)
```

The last form is distributive sugar. It means "apply these transforms to each child of `AnyOf`". It does not mean the fields are merged into one combined string first.

## Query semantics

### Ordered index queries

Ordered indexes are used through normal property filters and order clauses.

Examples:

```kotlin
Equals(
    Person { surname::ref } with "garcia lopez"
)
```

```kotlin
Prefix(
    Person { surname::ref } with "gar"
)
```

```kotlin
Orders(
    Person { surname::ref }.ascending(),
    Person { firstName::ref }.ascending()
)
```

Those map naturally onto:

- `Reference`
- `Normalize`
- `Reversed`
- `ReferenceToMax`
- `Multiple`

### Search index queries

Named search indexes are queried explicitly through search filters.

Examples:

```kotlin
Matches(
    "name" with "garcia lopez"
)
```

```kotlin
MatchesPrefix(
    "name" with "gar"
)
```

```kotlin
MatchesRegEx(
    "name" with Regex("^gar.*$")
)
```

This separation is intentional.

Property filters mean:
"match this property using property semantics"

Named search filters mean:
"search this named search surface using search semantics"

That keeps ordered equality/prefix/range logic separate from token search logic.

## Design advice

### Define both index types when needed

It is normal to define both an ordered index and a search index over similar fields.

Example:

```kotlin
indexes = {
    listOf(
        Multiple(
            Normalize(surname.ref()),
            firstName.ref()
        ),
        AnyOf(
            "name",
            surname.ref(),
            firstName.ref(),
            fullName.ref()
        ).normalize().split(WordBoundary)
    )
}
```

The first index supports ordered browsing and exact normalized matching.

The second index supports token search.

### Keep `AnyOf` focused

Only group fields that belong to the same user-facing search surface.

Good:
- patient name parts
- address parts
- aliases

Less good:
- unrelated fields grouped just because they are strings

If the search intent is different, define separate named search indexes.

### Use `Normalize` deliberately

`Normalize` is useful in both families:

- in ordered indexes, it gives normalized comparison and ordering
- in search indexes, it gives stable emitted search terms

But it does not change the index family by itself. `Normalize` alone is not token search.

### Choose `SplitOn` based on semantics

Use `Whitespace` when only spaces should separate terms.

Use `WordBoundary` when hyphen-like name boundaries should also behave as term boundaries.

### Write regexes for transformed search terms

`MatchesRegEx` runs against emitted search terms after index transforms.

So if the search index uses `Normalize`, the regex should target normalized tokens, not the original stored text.

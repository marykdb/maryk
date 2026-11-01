# Index operators and composition

Use [Index design](index-design.md) to choose a query pattern. This reference
covers operator construction and composition. Scalar ordered indexes normally emit
one key per record; collection and multi-entry indexes can emit several. Cursor
paging still returns a record once through its first eligible entry.

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

The tuple remains ordered by its components.

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

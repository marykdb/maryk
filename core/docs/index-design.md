# Index Design

Choose indexes from the application's filters, ordering and paging requirements.
Use the [operator reference](index-reference.md) for constructor details and
[index query examples](index-recipes.md) for filters and spatial candidates.

## Two index families

| Access pattern | Index family | Typical operators |
| --- | --- | --- |
| Sort, exact lookup, prefix/range scan, ordered paging | Ordered | Property reference, `Multiple`, `Reversed`, `Normalize` |
| Search terms across a named set of fields | Search | `AnyOf`, `Split`, `Normalize` |

### Ordered indexes

Compose an ordered tuple in the same leading-field order as the query. Scalar
indexes normally produce one key per record; collection indexes can fan out.

### Search indexes

Search indexes emit terms. Query the named surface with `Matches`, `MatchesPrefix`
or `MatchesRegEx`. They do not provide the application's record sort order.

### Why the distinction matters

A catalog can need both ordered browsing and token search over the same fields.
Define both when the access patterns require them; normalization alone is not tokenization.

## Ordered index building blocks

### Reference

Use `surname.ref()` for the stored value. See [Reference](index-reference.md#reference).

### `Normalize`

Transform a value before comparison or tokenization. See [Normalize](index-reference.md#normalize).

### `Reversed`

Reverse one component's order. See [Reversed](index-reference.md#reversed).

### `ReferenceToMax`

Use max-oriented positioning for a reference component. See [ReferenceToMax](index-reference.md#referencetomax).

### `Multiple`

Compose ordered components; leading parts determine useful lookup ranges.
See [Multiple](index-reference.md#multiple).

## Search index building blocks

### `Split`

Emit terms with `Whitespace` or `WordBoundary` boundaries. See [Split](index-reference.md#split).

### `AnyOf`

Combine fields belonging to one search surface. See [AnyOf](index-reference.md#anyof).

## Composition patterns

### Ordered composition

```kotlin
Multiple(Normalize(surname.ref()), firstName.ref(), Reversed(createdAt.ref()))
```

Compare normalized surname, then first name, then descending creation time.

### Search composition

```kotlin
AnyOf("name", surname.ref(), firstName.ref(), fullName.ref())
    .normalize().split(WordBoundary)
```

Normalize and tokenize each child field under the `name` surface.

### Composable syntax

Transforms on `AnyOf` apply to each child; they do not concatenate fields first.
See [equivalent forms](index-reference.md#composable-syntax).

## Query semantics

### Ordered index queries

Use property filters and order clauses. See [ordered queries](index-recipes.md#ordered-index-queries).

### Search index queries

Use the named search filters. See [search queries](index-recipes.md#search-index-queries).

### Geospatial candidate indexes

GeoHash narrows candidates for exact box/radius filters; it does not sort by distance.
See [spatial candidates](index-recipes.md#geospatial-candidate-indexes).

## Design advice

### Define both index types when needed

Use separate ordered and search indexes when both browsing and token search are required.

### Keep `AnyOf` focused

Group related name parts, addresses or aliases into their own user-facing surfaces.

### Use `Normalize` deliberately

Normalization changes comparison values; adding `Split` changes term boundaries.

### Choose `SplitOn` based on semantics

`Whitespace` separates whitespace; `WordBoundary` also handles hyphen-like boundaries.

### Write regexes for transformed search terms

Target emitted, normalized terms rather than the original stored string.

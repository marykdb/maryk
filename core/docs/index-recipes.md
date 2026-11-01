# Querying indexes

Start with [Index design](index-design.md) and the [operator reference](index-reference.md).

## Query semantics

### Ordered index queries

Ordered indexes are used through normal property filters and order clauses.

Examples:

```kotlin
Equals(
    Person.ref { surname } with "garcia lopez"
)
```

```kotlin
Prefix(
    Person.ref { surname } with "gar"
)
```

```kotlin
Orders(
    Person.ref { surname }.ascending(),
    Person.ref { firstName }.ascending()
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

### Geospatial candidate indexes

`GeoHash(location.ref(), precisionBits = 32u)` narrows `GeoWithinBox` and
`GeoWithinRadius` scans. It stores one stable geohash cell per record. The
planner covers the query bounds with at most 256 byte-aligned prefixes,
reducing precision when needed, and exact geometry removes false positives.
Geohash ordering is spatial cell order; it does not provide distance sorting or
nearest-neighbour ranking.

That keeps ordered equality/prefix/range logic separate from token search logic.

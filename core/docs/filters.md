# Filters

Filters narrow query results. They can be applied to [Get](query.md#get) and [Scan](query.md#scan) requests.

Maryk has two filter groups:

- property filters
- named search filters

Property filters target explicit property references.

Named search filters target a named search surface such as `"name"`.

For index design and when to use ordered indexes versus search indexes, see [Index Design](index-design.md). This page focuses on the filters themselves.

## Property filters

Property filters target one or more explicit [property references](properties/references.md).

Use them when the query is about a specific field.

Examples:

```kotlin
Equals(
    Person { surname::ref } with "Mous"
)
```

```kotlin
GreaterThan(
    Person { age::ref } with 42
)
```

```kotlin
Prefix(
    Person { surname::ref } with "Mo"
)
```

### Exists

Checks whether a value exists.

```kotlin
Exists(
    propertyReference1,
    propertyReference2,
    propertyReference3,
)
```

### Equals

Matches when a property equals the provided value.

```kotlin
Equals(
    stringPropertyReference with "value",
    numberPropertyReference with 5,
)
```

### GreaterThan

Matches when the value is greater than the provided value.

```kotlin
GreaterThan(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### GreaterThanEquals

Matches when the value is greater than or equal to the provided value.

```kotlin
GreaterThanEquals(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### LessThan

Matches when the value is less than the provided value.

```kotlin
LessThan(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### LessThanEquals

Matches when the value is less than or equal to the provided value.

```kotlin
LessThanEquals(
    stringPropertyReference with "value",
    intPropertyReference with 42,
)
```

### Range

Checks whether the value falls within a range.

```kotlin
Range(
    intPropertyReference with 2..42,
    stringPropertyReference with ValueRange(
        from = "abba",
        to = "zeplin",
        inclusiveTo = false,
    )
)
```

### Prefix

Matches values that start with the given prefix.

```kotlin
Prefix(
    stringPropertyReference with "val",
    anotherStringPropertyReference with "do",
)
```

### RegEx

Matches values that satisfy a regular expression.

```kotlin
RegEx(
    stringPropertyReference with Regex("[A-Z]+al.*"),
    anotherStringPropertyReference with Regex("[E-Z]+al.*"),
)
```

### ValueIn

Matches when the value is included in a provided set.

```kotlin
ValueIn(
    stringPropertyReference with setOf("a", "b", "value"),
    intPropertyReference with setOf(1, 3, 5),
)
```

## Named search filters

Named search filters target a named search surface instead of one explicit property reference.

Use them when the query should behave like a search box.

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

### Matches

`Matches` does exact term matching on the emitted search terms of a named search surface.

If the query text becomes multiple terms, all resulting query terms must match.

Example:

```kotlin
Matches(
    "name" with "van der waals"
)
```

This behaves as:

- transform the query according to the search index definition
- if that produces multiple terms, require all of them to be present

### MatchesPrefix

`MatchesPrefix` works like `Matches`, but query terms are treated as prefixes.

Example:

```kotlin
MatchesPrefix(
    "name" with "gar"
)
```

If the query produces multiple terms, all of those prefixes must match.

### MatchesRegEx

`MatchesRegEx` applies a regex to the emitted search terms of a named search surface.

Example:

```kotlin
MatchesRegEx(
    "name" with Regex("^gar.*$")
)
```

If the search surface transforms values before indexing, the regex runs against those transformed terms.

## Combining filters

Filters can be combined to form more complex logic.

### And

`And` returns `true` only if all included filters match.

```kotlin
And(
    Equals(
        stringPropertyReference with "value"
    ),
    GreaterThan(
        intPropertyReference with 42
    )
)
```

You can also mix filter groups:

```kotlin
And(
    Matches(
        "name" with "garcia"
    ),
    Equals(
        Person { active::ref } with true
    )
)
```

### Or

`Or` returns `true` if any of the included filters matches.

```kotlin
Or(
    Equals(
        stringPropertyReference with "value"
    ),
    GreaterThan(
        intPropertyReference with 42
    )
)
```

### Not

`Not` inverts the result of the nested filters. If multiple filters are provided it behaves as `And` and negates the combined result.

```kotlin
Not(
    Equals(
        stringPropertyReference with "value"
    ),
    Exists(intPropertyReference),
)
```

## Choosing the right filter

Use a property filter when:

- the query is about one explicit field
- exact field semantics matter

Use a named search filter when:

- the query should search one named search surface
- the query should behave like user-entered search text

Rule of thumb:

- `Equals(surname.ref() with "Mous")` means "surname equals Mous"
- `Matches("name" with "mous")` means "search the named name surface"

# Property Reference Graphs

Property reference graphs define which properties to retrieve in a query. They allow you to select specific fields and nested structures instead of fetching entire objects. A graph can range from a list of top-level properties to a combination of embedded graphs, map keys and type-specific selections.

The examples below use a fictitious `User` model for a social application. It contains the
following properties:

- `name`: String
- `age`: Int
- `address`: embedded `Address` with `street` and `city`
- `preferences`: Map<String, Preference> where `Preference` has a `value` field
- `contact`: multi-type with variants like `Email` (property `address`) and `Phone` (property `number`)
- `roles`: Set<String>

## Basic usage

Use the `graph` DSL to list the properties you want to retrieve:

```kotlin
val graph = User.graph {
    listOf(
        name,
        age
    )
}
```

This graph can then be supplied to a request through the `select` parameter.

Multiple branches for the same property are combined. For example, two map-key
branches select both entries, and separate embedded branches select the union of
their child fields. Selecting the whole property includes all its children.
Serialized property names are local to their graph branch. For example,
`details.first` is represented as `[{"details":["first"]}]`, rather than a flattened
`["details.first"]` reference. Nonlocal references are rejected so an ancestor
cannot be dropped and its child index interpreted as a different root property.

Selections apply to current and historical reads, live snapshots, and replacement
values in live updates.

## Embedded graphs

For embedded data models, create a sub-graph by calling `graph` on the embedded definition:

```kotlin
val graph = User.graph {
    listOf(
        graph(address) {
            listOf(
                street,
                city
            )
        }
    )
}
```

## Map keys

Maps can target specific keys. Optionally, you can define a sub-graph for the map value if it is an embedded model:

```kotlin
val graph = User.graph {
    listOf(
        preferences.graph("theme") {
            listOf(
                value
            )
        }
    )
}
```

Map-key selections use the map's declared key codec in JSON and YAML. Brackets
inside string keys are preserved, and simple key types such as geographic points
and value objects use the same textual representation as their definitions.

To retrieve a whole map entry without selecting its fields, reference that entry:

```kotlin
val graph = User.graph {
    listOf(
        preferences["notifications"]
    )
}
```

## Multi-type values

For multi-type properties, `withTypeGraph` lets you build a graph for a specific type:

```kotlin
val graph = User.graph {
    listOf(
        contact.withTypeGraph(ContactType.Email) {
            listOf(
                address
            )
        }
    )
}
```

## Combining operations

Graphs can mix different operations to construct complex selections:

```kotlin
val graph = User.graph {
    listOf(
        name,
        roles,
        preferences["language"],
        graph(address) {
            listOf(city)
        },
        contact.withTypeGraph(ContactType.Email) {
            listOf(address)
        }
    )
}
```

This graph combines basic properties, map keys, type-specific selections, and nested embedded graphs.

Incrementing-map updates for selected keys use resolved additions with the
allocated datastore keys. Allocation commands are excluded from partial-map
projections because the client does not hold the complete map needed to allocate
the same next key. Unselected additions are omitted.

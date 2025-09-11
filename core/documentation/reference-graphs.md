# Property Reference Graphs

Property reference graphs define which properties to retrieve in a query. They allow selecting
specific fields and nested structures rather than fetching entire objects. A graph can be as
simple as a list of top-level properties or as complex as a combination of embedded graphs,
map keys, and type-specific selections.

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

To select a key without additional fields, simply reference the key:

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

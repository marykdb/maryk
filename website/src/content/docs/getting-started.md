---
title: Getting Started
description: Add dependencies, define your first model, validate, serialize, and choose a datastore.
---

Maryk runs on Kotlin Multiplatform and ships as modular artifacts. This guide shows the minimal setup and a first round‑trip from model → object → validation → serialization → storage.

## Dependency

Add the core dependency to your target(s):

```kotlin
dependencies {
  implementation("io.maryk:maryk-core:<version>")
}
```

For stores, add one engine:

```kotlin
// In-memory (tests/dev)
implementation("io.maryk:store-memory:<version>")

// Embedded RocksDB (apps/servers)
implementation("io.maryk:store-rocksdb:<version>")

// FoundationDB (JVM server)
implementation("io.maryk:store-foundationdb:<version>")
```

## Define a model

```kotlin
object Person : RootDataModel<Person>() {
  val firstName by string(index = 1u)
  val lastName by string(index = 2u)
  val dateOfBirth by date(index = 3u)
}
```

Create and validate:

```kotlin
val john = Person.create {
  firstName with "John"
  lastName with "Smith"
  dateOfBirth with LocalDate(2017, 12, 5)
}

Person.validate(john)
```

## Serialize

```kotlin
val json = Person.writeJson(john)
val fromJson = Person.readJson(json)
```

Also see YAML/ProtoBuf in [Serialization](/serialization/).

## Pick a datastore

Use in‑memory for tests:

```kotlin
InMemoryDataStore.open(
  keepAllVersions = true,
  dataModelsById = mapOf(1u to Person)
).use { store ->
  store.execute(Person.add(john))
}
```

Then advance to [Stores](/stores/) for RocksDB or FoundationDB and migration tips.

## CLI

Use the [CLI](/cli/) to explore models and records, run scans, and edit values without writing code.

## App

Use the [Maryk App](/app/) for a desktop UI to browse models and edit records.

```bash
./gradlew :app:run
```

## Next steps

- Learn how models and properties work in [Data design](/data-modeling/data-design/).
- Explore queries, filters, and aggregations in [Querying](/querying/).
- Check out how to persist data in [Stores](/stores/).
- Browse and edit data interactively with the [CLI](/cli/).

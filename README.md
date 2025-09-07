[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Download](https://img.shields.io/maven-central/v/io.maryk/maryk-core)](https://central.sonatype.com/artifact/io.maryk/maryk-core)

# Maryk: Cross-Platform Data Modeling and Storage

Maryk is a **Kotlin Multiplatform** framework for defining, validating, serializing, and storing data models consistently across multiple platforms, including **iOS**, **macOS**, **Linux**, **Windows**, **Android**, **JVM**, and **JavaScript**. With a fully version-aware data store and flexible querying, Maryk makes it easy to maintain complex data structures while ensuring backward compatibility and efficient data handling.

The RocksDB persistence layer is available for the **JVM**, **iOS**, **macOS**, **Android** and **Linux**.

## Key Features

- **Unified Data Modeling**: Define your [data models](core/documentation/datamodel.md) once and use them everywhere, ensuring a single source of truth across platforms.

- **Flexible Property Types and Inheritance**: Create models with a variety of [property types](core/documentation/properties/properties.md), and reuse model structures to build complex data hierarchies.

- **Built-in Validation**: Enforce data quality with [validations](core/documentation/properties/properties.md#validation) such as required fields, uniqueness, min/max constraints, and regex checks.

- **Cross-Platform Serialization**: Seamlessly [serialize and deserialize](core/documentation/serialization.md) data as JSON, YAML, or Protocol Buffers, facilitating easy communication between clients and services.

- **Model Serialization & Compatibility**: Serialize your schemas themselves and run compatibility checks across different clients, ensuring smooth upgrades and migrations.

- **Version-Aware Storage and Queries**: Store data in [NoSQL data stores](store/memory/README.md) (in-memory/[RocksDB](store/rocksdb/README.md)/[FoundationDB](store/foundationdb/README.md)) and leverage [versioning](core/documentation/versioning.md) to request historical states, compare past values, and minimize bandwidth by fetching only changed fields.

- **Data Aggregations & Insights**: Perform [aggregations](core/documentation/aggregations.md) (count, sum, average, min/max, grouped by time intervals or enums) for richer analytics and decision-making.

## Getting Started

1. **Add Maryk Core Dependency**:  
In your `build.gradle.kts`:
```kotlin
implementation("io.maryk:maryk-core:<version>")
```

2. **Define Your Data Models**:  
Create a Kotlin data model:
```kotlin
object Person : RootDataModel<Person>() {
   val firstName by string(index = 1u)
   val lastName by string(index = 2u)
   val dateOfBirth by date(index = 3u)
}
```

3. **Create and Validate Instances**:  
```kotlin
val johnSmith = Person.create {
   firstName += "John"
   lastName += "Smith"
   dateOfBirth += LocalDate(2017, 12, 5)
}

// Validate the object
Person.validate(johnSmith)
```

4. **Serialize Your Data Objects**:  
```kotlin
// Serialize to JSON
val json = Person.writeJson(johnSmith)

// Deserialize from JSON
val personFromJson = Person.readJson(json)
```

5. **Choose a Data Store**:
  - [Memory](store/memory/README.md) — in‑memory, non‑persistent, fastest feedback for dev/tests.
  - [RocksDB](store/rocksdb/README.md) — embedded, persistent, high performance on a single node across desktop/mobile/server.
  - [FoundationDB](store/foundationdb/README.md) — distributed, transactional persistence on the JVM with strong consistency and time‑travel support.
  
  See the overview and guidance in [store/README.md](store/README.md) for when to pick each engine.

## Documentation

For detailed information, check out:

- [Core](core/README.md) – Data models, queries, parsers, readers.
- [Library](lib/README.md) – Shared utilities for things like Strings and ByteArrays.
- [JSON](json/README.md) & [YAML](yaml/README.md) – Streaming parsers and writers.
- [Generator](generator/README.md) – Code generation from YAML and JSON models.
- [Test Library](testlib/README.md) – Testing utilities and helpers.
- [DataFrame Integration](dataframe/README.md) – DataFrame helper functions for Maryk objects.
- **Stores**:
  - [Shared](store/shared/README.md) – Shared logic for building stores.
  - [Memory](store/memory/README.md) – In-memory store (non-persistent).
  - [RocksDB](store/rocksdb/README.md) – Persistent, high-performance store.
  - [FoundationDB](store/foundationdb/README.md) – Persistent, scalable transactional store (JVM).
  - [Tests](store/test/README.md) – Common tests to ensure store reliability.

## Contributing

We welcome contributions through feature requests, issue reports, and pull requests.

**Your involvement helps Maryk grow and improve!**

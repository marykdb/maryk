[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Download](https://img.shields.io/maven-central/v/io.maryk/maryk-core)](https://central.sonatype.com/artifact/io.maryk/maryk-core)

# Maryk: Kotlin Multiplatform Data Modeling and Storage

Maryk lets you define a strongly typed data model once, then use that same model for validation, serialization, querying, storage, tooling, and sync across Kotlin Multiplatform targets.

Use Maryk when you want:

- One schema shared by clients, servers, tools, and tests.
- Stable binary-compatible property indexes instead of reflection-heavy runtime mapping.
- Version-aware storage: historic reads, change queries, update streams, and efficient sync.
- One request API across in-memory, embedded RocksDB, distributed FoundationDB, and remote stores.
- Portable JSON, YAML, and ProtoBuf serialization built from the same model definitions.

Maryk is a good fit for local-first apps, cross-platform products, Kotlin-heavy backends, tools that need typed data files, and systems where schema evolution and incremental sync matter.

Start with the [website](https://marykdb.github.io/maryk/) for the best reading path. Source docs live in this repository and the website is maintained in `website/`.

## Platform Support

Maryk is Kotlin Multiplatform. Platform support depends on the module:

- Core modeling, validation, querying, JSON/YAML serialization, generator support, Memory Store, shared store logic, test models, and test helpers target JVM, Android, JS, WasmJS, Linux, Windows, iOS, macOS, watchOS, tvOS, and Android Native.
- File IO targets JVM, Linux, Windows, iOS, macOS, watchOS, tvOS, and Android Native.
- RocksDB Store targets JVM, Android, Linux, Windows, iOS, macOS, watchOS, tvOS, and Android Native through the `rocksdb-multiplatform` bindings.
- FoundationDB Store targets JVM, Linux, and macOS where the FoundationDB client library (`libfdb_c`) is available.
- Remote Store targets JVM, Linux, and macOS.
- CLI native binaries target Linux and macOS, with JVM execution also available.
- The desktop App runs on the JVM.

## Getting Started

Add the core dependency:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.maryk:maryk-core:<maryk-version>")
}
```

Define a model:

```kotlin
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.date
import maryk.core.properties.definitions.string
import maryk.lib.time.LocalDate

object Person : RootDataModel<Person>() {
    val firstName by string(index = 1u)
    val lastName by string(index = 2u)
    val dateOfBirth by date(index = 3u)
}

val johnSmith = Person.create {
    firstName with "John"
    lastName with "Smith"
    dateOfBirth with LocalDate(2017, 12, 5)
}

Person.validate(johnSmith)

val json = Person.Serializer.writeJson(johnSmith, pretty = true)
val fromJson = Person.Serializer.readJson(json)
```

For a complete model → store → query flow, read [Getting Started](website/src/content/docs/getting-started.mdx) or [First Store Tutorial](website/src/content/docs/tutorials/first-store.mdx).

## Storage Engines

- [Memory](store/memory/README.md): in-memory, non-persistent, best for tests and fast local feedback.
- [RocksDB](store/rocksdb/README.md): embedded persistent storage for desktop, mobile, and single-node server use.
- [FoundationDB](store/foundationdb/README.md): distributed transactional storage on supported platforms with `libfdb_c`.
- [Remote Store](store/remote/README.md): expose a local Maryk store over HTTP and optionally SSH-tunnel it.

See [store/README.md](store/README.md) for the decision guide.

## Documentation Map

- [Core](core/README.md) – Data models, property types, keys, queries, versioning, serialization.
- [Library](lib/README.md) – Shared utilities for things like Strings and ByteArrays.
- [File](file/README.md) – Minimal cross-platform file IO layer used by tooling and stores.
- [JSON](json/README.md) & [YAML](yaml/README.md) – Streaming parsers and writers.
- [Generator](generator/README.md) – Code generation from YAML and JSON models.
- [Test Library](testlib/README.md) – Testing utilities and helpers.
- [DataFrame Integration](dataframe/README.md) – DataFrame helper functions for Maryk objects.
- [CLI](cli/README.md) – Interactive terminal client for browsing and editing stores.
- [App](app/README.md) – Desktop UI for browsing and editing stores.
- **Stores**:
  - [Shared](store/shared/README.md) – Shared logic for building stores.
  - [Memory](store/memory/README.md) – In-memory store (non-persistent).
  - [RocksDB](store/rocksdb/README.md) – Persistent, high-performance store.
  - [FoundationDB](store/foundationdb/README.md) – Persistent, scalable transactional store (multiplatform where `libfdb_c` is available).
  - [Remote](store/remote/README.md) – HTTP/SSH gateway and client for remote access.
  - [Tests](store/test/README.md) – Common tests to ensure store reliability.

## Repository Development

Useful commands:

```bash
./gradlew jvmTest
./gradlew :store:memory:jvmTest
./gradlew :cli:jvmTest
cd website && yarn build
```

When editing website pages generated from repository docs, update the source file listed in [website/README.md](website/README.md).

## Contributing

Issues, discussions, docs fixes, examples, store improvements, and PRs are welcome. Good first contributions usually live in docs, examples, tests, CLI/App workflows, or store-specific edge cases.

# Maryk: Cross-Platform Data Modeling and Storage

Maryk is a Kotlin Multiplatform project that allows developers to define, validate,
serialize, and store data models across multiple platforms, including iOS, macOS, Android, JVM,
and JavaScript. 

With Maryk, you can easily create complex data structures, enabling
efficient and seamless cross-platform communication for your applications featuring a fully 
version-aware data store and query engine. Its powerful capabilities and ease of use make
it an excellent choice for managing and storing data in Kotlin-based applications.

## Features

- **Unified data modeling**: Define your [data models](core/documentation/datamodel.md) once and use them seamlessly across
  all supported platforms, making it easy to 
  create cross-platform applications with consistent data structures.

- **Flexible data model inheritance**: Maryk allows you to include [properties of different
  types](core/documentation/properties/properties.md) within a data model and create a generic 
  root data model that can contain different varieties of data models. This enables you to 
  design more complex and expressive data structures.

- **Built-in validation**: Easily [validate your data objects](core/documentation/properties/properties.md#validation) with a variety of constraints,
  such as required, unique, min/max values or sizes, and regular expressions. This ensures
  that your data is always accurate and consistent.

- **Cross-platform serialization**: Supports [JSON, YAML, and Protocol Buffers serialization](core/documentation/serialization.md)
  formats for efficient data transportation of data objects between platforms, allowing your app to
  communicate with any platform with ease.

- **Data Model Serialization and compatibility check**: Maryk also enables [serialization of the data models
  themselves](core/documentation/serialization.md), making it easy to check compatibility between different models on different clients or storage.
  This also allows you to transport data models across platforms, ensuring that your application's
  data structures are compatible even when running against outdated clients.

- **NOSQL data stores**: Store and query data efficiently using provided implementations
  for NOSQL data stores like the [in-memory store](store/memory/README.md) or the 
  [RocksDb backed store](store/rocksdb/README.md). The included data stores are optimized to work across platforms, ensuring consistent performance on all devices.

- **Full versioning support**: Maryk's data stores are built with [full versioning](core/documentation/versioning.md) 
  in mind, allowing you to easily access older versions or the changes made to your data objects at
  any point in time. This makes it possible to maintain an audit trail and provides
  feature-rich data management capabilities for your application.

- **Efficient version-aware data querying**: As Maryk supports full versioning, you can
  easily [request only the changed values](core/documentation/query.md) from a certain time frame or see the difference
  between two data objects. This allows for more efficient data transportation and reduces
  bandwidth usage when synchronizing data across platforms.

- **Aggregations and insights**: Aggregate your data and gain valuable insights on your
  stored data with built-in functionalities like count, sum, average, min/max value, and
  other statistical aggregations. Group your data based on date units like hour/week/month/year 
  or by enum value to gain a deeper understanding of your data.

## Getting Started

To get started with Maryk, follow these steps:

1. Add Maryk's core dependency to your Kotlin Multiplatform project Gradle configuration:

```gradle
implementation "io.maryk:maryk-core:$version"
```

2. Define your data models:

```kotlin
object Person : RootModel<Person> { 
    val firstName by string(index = 1u)
    val lastName by string(index = 2u)
    val dateOfBirth by date(index = 3u)
}
```

3. Create and validate data objects:

```kotlin
val johnSmith = Person.run {
    create(
        firstName with "John",
        lastName with "Smith",
        dateOfBirth with LocalDate(2017, 12, 5),
    )
}

// Validate the object
Person.validate(johnSmith) 
```

4. Serialize your data objects in your preferred format (e.g., JSON, YAML, or ProtoBuf) and deserialize them on another platform.

```kotlin
User.writeJson(user, jsonWriter)

val user = User.readJson(reader)
```

5. Choose an appropriate data store to store and query your data objects efficiently. Available store implementations include:
  - [In-memory store](store/memory/README.md) (non-persistent, suitable for testing)
  - [RocksDB based store](store/rocksdb/README.md) (persistent, suitable for JVM/Android/iOS/Mac)


## Documentation

For more details on how to use Maryk, explore the documentation within the modules of the project repository.

All core projects are multi-platform kotlin projects, and most support JS, macOS, iOS, Android and the JVM. .

- [core](core/README.md) - The core of Maryk, including models, properties, queries, parsers, and readers.
- [library](lib/README.md) - A set of multi-platform utilities, such as String, Hex, UUID, and more.
- [json](json/README.md) - A streaming JSON parser and writer.
- [yaml](yaml/README.md) - A streaming YAML parser and writer.
- [generator](generator/README.md) - A code generator for Kotlin and protobuf schemas from YAML Models.
- [test library](testlib/README.md) - A library to assist with writing tests.
- [test models](testmodels/README.md) - Maryk Models useful in testing library code.
- Stores
  - [Shared](store/shared/README.md) - Shared code that is useful in building Maryk stores.
  - [Memory](store/memory/README.md) - An in-memory store implementation, useful for testing purposes.
  - [RocksDB](store/rocksdb/README.md) - A RocksDB store implementation.
  - [test](store/test/README.md) - Common tests to validate the correctness of store implementations.

## Contributing

We welcome any feature requests, issue reports, and merge requests from the community. Feel free to open issues or submit pull requests on the GitHub repository.

## Future Roadmap

- Support for proper in cloud storage by implementing a store based on the Cassandra API which is supported by many cloud store providers.
- KTOR serialization plugin to pass data objects as values and have them serialized automatically.



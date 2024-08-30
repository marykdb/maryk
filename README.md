[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Download](https://img.shields.io/maven-central/v/io.maryk/maryk-core)](https://central.sonatype.com/artifact/io.maryk/maryk-core)

# Maryk: Cross-Platform Data Modeling and Storage

Maryk is a Kotlin Multiplatform project designed for developers to define, validate, serialize, and store data models 
across various platforms, including iOS, macOS, Android, JVM, and JavaScript. Currently, storage is only supported on 
the JVM.

With Maryk, you can create complex data structures that facilitate efficient and seamless cross-platform communication. 
It features a fully version-aware data store and query engine, making it an excellent choice for managing and storing 
data in Kotlin-based applications.

## Features

- **Unified Data Modeling**: Define your [data models](core/documentation/datamodel.md) once and use them seamlessly 
  across all supported platforms. This consistency simplifies the creation of cross-platform applications.

- **Flexible Data Model Inheritance**: Maryk allows you to include [properties of different types](core/documentation/properties/properties.md) 
  within a data model. You can create a generic root data model that accommodates various data models, enabling more 
  complex and expressive designs.

- **Built-in Validation**: Easily [validate your data objects](core/documentation/properties/properties.md#validation) 
  with various constraints such as required fields, uniqueness, min/max values or sizes, and regular expressions. This 
  ensures your data remains accurate and consistent.

- **Cross-Platform Serialization**: Supports [JSON, YAML, and Protocol Buffers serialization](core/documentation/serialization.md)
  formats for efficient data transportation between platforms, allowing seamless communication across different environments.

- **Data Model Serialization and Compatibility Check**: Maryk enables [serialization of the data models themselves](core/documentation/serialization.md),
  facilitating compatibility checks between different models on various clients or storage. This feature ensures that 
  your application's data structures remain compatible even when running against outdated clients.

- **NoSQL Data Stores**: Efficiently store and query data using provided implementations for NoSQL data stores, such as
  the [in-memory store](store/memory/README.md) and the [RocksDB backed store](store/rocksdb/README.md).

- **Full Versioning Support**: Built with [full versioning](core/documentation/versioning.md) in mind, Maryk allows easy
  access to older versions or changes made to your data objects at any time. This capability supports maintaining an 
  audit trail and provides rich data management features.

- **Efficient Version-Aware Data Querying**: With full versioning support, you can [request only the changed values](core/documentation/query.md)
  from a specific timeframe or compare two data objects. This reduces bandwidth usage during data synchronization across
  platforms.

- **Aggregations and Insights**: [Aggregate your data](core/documentation/aggregations.md) to gain valuable insights with
  built-in functionalities like count, sum, average, min/max value, and other statistical aggregations. Group your data
  by date units (hour/week/month/year) or by enum value for deeper analysis.

## Getting Started

To get started with Maryk, follow these steps:

1. **Add Maryk's Core Dependency**: Include Maryk's core dependency in your Kotlin Multiplatform project Gradle configuration:

   ```gradle
   implementation "io.maryk:maryk-core:$version"
   ```

2. **Define Your Data Models**: Create your data models using Kotlin:

   ```kotlin
   object Person : RootDataModel<Person>() { 
       val firstName by string(index = 1u)
       val lastName by string(index = 2u)
       val dateOfBirth by date(index = 3u)
   }
   ```

3. **Create and Validate Data Objects**: Instantiate and validate your data objects:

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

4. **Serialize Your Data Objects**: Serialize your data objects in your preferred format (e.g., JSON, YAML, or ProtoBuf) and deserialize them on another platform:

   ```kotlin
   User.writeJson(user, jsonWriter)

   val user = User.readJson(reader)
   ```

5. **Choose an Appropriate Data Store**: Select a suitable data store for efficient storage and querying of your data objects. Available implementations include:
  - [In-memory store](store/memory/README.md) (non-persistent, suitable for testing)
  - [RocksDB based store](store/rocksdb/README.md) (persistent, suitable for JVM/Android/iOS/Mac)

## Documentation

For more details on how to use Maryk, explore the documentation within the modules of the project repository. All core
projects are multi-platform Kotlin projects, supporting JS, macOS, iOS, Android, and the JVM.

- [core](core/README.md) - The core of Maryk, including models, properties, queries, parsers, and readers.
- [library](lib/README.md) - A set of multi-platform utilities, such as String, Hex, UUID, and more.
- [json](json/README.md) - A streaming JSON parser and writer.
- [yaml](yaml/README.md) - A streaming YAML parser and writer.
- [generator](generator/README.md) - A code generator for Kotlin and protobuf schemas from YAML Models.
- [test library](testlib/README.md) - A library to assist with writing tests.
- [test models](testmodels/README.md) - Maryk Models useful in testing library code.
- [dataframe](dataframe/README.md) - Provides DataFrame helper functions for Maryk objects.
- Stores:
  - [Shared](store/shared/README.md) - Shared code useful in building Maryk stores.
  - [Memory](store/memory/README.md) - An in-memory store implementation, useful for testing purposes.
  - [RocksDB](store/rocksdb/README.md) - A RocksDB store implementation.
  - [test](store/test/README.md) - Common tests to validate the correctness of store implementations.

## Contributing

We welcome feature requests, issue reports, and merge requests from the community. Feel free to open issues or submit 
pull requests on the GitHub repository.

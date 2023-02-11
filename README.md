# Maryk

Maryk is a multi-platform library designed to help you store, query, and send data in a structured, type-strict manner. This 
library provides a versioned data store, allowing you to request only changed data or receive live updates.

## Supported Platforms

Maryk is a Kotlin-based library, and it provides support for a wide range of platforms, including
JavaScript (JS), macOS, iOS, Android, and Java Virtual Machine (JVM).

## Projects

Maryk is composed of several sub-projects, each with its specific functionality:
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

## Getting Started

To get started with Maryk, be sure to check out the README file for each sub-project in the Maryk library.
These files provide more in-depth information on how to define data structures, query data, set up a data store, and more.

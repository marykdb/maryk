# Maryk

Maryk is a way of defining data structures which enable you to validate, serialize or store them
in a data store. In the data store all values are stored with version so it is easy to query any updates.

## Projects
All core projects are multi-platform kotlin projects and support JS and JVM

- [core](core/README.md) - Contains the core of Maryk like models, properties, queries,
  parsers and readers. 
- [library](lib/README.md) - Contains all multi-platform utilities needed for core 
  projects like Base64, String, Date, UUID and more
- [json](json/README.md) - A streaming JSON parser and writer
- [yaml](yaml/README.md) - A streaming YAML parser and writer
- [generator](generator/README.md) - Generator for Kotlin code and protobuf schemas from Models.
- [test library](testlib/README.md) - Library which contains methods to help with writing tests
- [test models](testmodels/README.md) - Maryk Models which can be useful in testing library code
- Stores
    - [Shared](store/shared/README.md) - Shared code useful to build Maryk stores
    - [Memory](store/memory/README.md) - In memory store implementation. Does not persist to disk. 
      Useful for testing.
    - [RocksDB](store/rocksdb/README.md) - RocksDB store implementation
    - [test](store/test/README.md) - Contains common tests to test if a store implementation is 
      working correctly

# Maryk

Maryk is a library which helps you to store, query and send data in a structured and type strict way over multiple 
platforms. The data store stores any value with a version, so it is possible to request only the changed data or live 
listen for updates. 

## Projects
All core projects are multi-platform kotlin projects, and most support JS, macOS, iOS, Android and the JVM. For more details 
on how to define data structures and query the data check out the [core](core/README.md) module. To set up an actual data 
store check out the [RocksDB](store/rocksdb/README.md) module.

- [core](core/README.md) - Contains the core of Maryk like models, properties, queries,
  parsers and readers. 
- [library](lib/README.md) - Contains all multi-platform utilities needed for core 
  projects like Base64, String, Date, UUID and more
- [json](json/README.md) - A streaming JSON parser and writer
- [yaml](yaml/README.md) - A streaming YAML parser and writer
- [generator](generator/README.md) - Generator for Kotlin code and protobuf schemas from YAML Models.
- [test library](testlib/README.md) - Library which contains methods to help with writing tests
- [test models](testmodels/README.md) - Maryk Models which can be useful in testing library code
- Stores
    - [Shared](store/shared/README.md) - Shared code useful to build Maryk stores
    - [Memory](store/memory/README.md) - In memory store implementation. Does not persist to disk. 
      Useful for testing.
    - [RocksDB](store/rocksdb/README.md) - RocksDB store implementation
    - [test](store/test/README.md) - Contains common tests to test if a store implementation is 
      working correctly

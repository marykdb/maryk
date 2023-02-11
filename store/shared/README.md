# Maryk DataStore shared module

This module contains common code used by all Maryk DataStore implementations. It provides the core functionality
for creating and managing DataModels and executing queries. This module is designed to be used by specific 
implementations of the DataStore, such as the [InMemoryDataStore](../memory/README.md) and [RocksDBDataStore](../rocksdb/README.md).

## Key features

- A unified interface for executing data operations, such as adding, modifying, and retrieving data.
- Support for versioning and keeping multiple versions of the same data.
- A modular design that allows for easy integration with different DataStore implementations.
- Integration with Maryk's DataModel and Property system, allowing for powerful and flexible data modeling.

## Usage

This module is not meant to be used directly, but rather to be utilized by specific DataStore 
implementations. For usage instructions and examples, see the documentation for the specific DataStore you are using,
such as the [InMemoryDataStore](../memory/README.md) and [RocksDBDataStore](../rocksdb/README.md).

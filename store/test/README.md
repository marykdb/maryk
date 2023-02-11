# Maryk Store tests

This subproject contains generic tests that can be used to verify the functionality 
of any Maryk data store implementation. These tests are designed to validate the correctness
of a store's data storage and retrieval operations, as well as its handling of versioning.

## Usage

To test a specific data store, simply create a test case that uses the `runDataStoreTests(DataStore)` function. 
This function takes a `DataStore` instance as a parameter and runs a suite of tests on it. The tests will validate 
the store's behavior in various scenarios, including adding and retrieving data and versioning.

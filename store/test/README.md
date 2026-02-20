# Maryk Store tests

This subproject contains generic tests for Maryk data store implementations.
It validates storage/retrieval behavior and versioning semantics across engines.

## Usage

To test a specific data store, create a test case that calls `runDataStoreTests(...)`
with your store instance. The suite verifies scenarios such as add/get/change/delete,
scan behavior, and versioning consistency.

# Maryk Store tests

This subproject contains generic tests for Maryk data store implementations.
It validates storage/retrieval behavior and versioning semantics across engines.

## Usage

To test a specific data store, create a test case that calls `runDataStoreTests(...)`
with your store instance. The suite verifies scenarios such as add/get/change/delete,
scan behavior, and versioning consistency.

## What it protects

- Shared request semantics across Memory, RocksDB and FoundationDB.
- Key, index and unique handling.
- Current and historic reads.
- Change and update responses.
- Soft and hard deletes.
- Listener/update behavior where supported.

## Adding tests

Add generic behavior here when every store should pass it. Add engine-specific tests in the engine module when behavior depends on RocksDB, FoundationDB or native platform details.

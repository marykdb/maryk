# Maryk Store tests

This subproject contains generic tests for Maryk data store implementations.
It validates storage/retrieval behavior and versioning semantics across engines.

## Usage

To test a specific data store, create a test case that calls `runDataStoreTests(...)`
with your store instance. The suite verifies scenarios such as add/get/change/delete,
scan behavior, and versioning consistency.

## What it protects

- Shared request semantics across Memory, IndexedDB, RocksDB and FoundationDB.
- Key, index and unique handling.
- Current and historic reads.
- Change and update responses.
- Cursor-paged update history, tied versions, complete selected revisions,
  and creation/deletion events after key reuse.
- Soft and hard deletes.
- Listener/update behavior where supported.

## Adding tests

Add generic behavior here when every store should pass it. Add engine-specific tests in the engine module when behavior depends on RocksDB, FoundationDB or native platform details.

`CareQueryRegressionCases` holds the shared scan and history regressions;
backend test classes supply isolated stores and execute those cases. Keep
regressions with the implementation commit that fixes them. Native RocksDB
iterator-loop regressions also use an iterator budget because a coroutine
timeout cannot interrupt a runnable native scanner.

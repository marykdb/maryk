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
- Atomic change-sets across models, late validation and unique-conflict rollback,
  version and value guards, competing writes, retry receipts, and suppression of
  notifications for aborted changes.
- Serializable transactions: staged reads, repeated writes, hard deletion,
  savepoints, read-only scopes, predicate conflicts and commit recovery.
- Change and update responses.
- Cursor-paged update history, tied versions, complete selected revisions,
  and creation/deletion events after key reuse.
- Soft and hard deletes.
- Listener/update behavior where supported.

## Adding tests

Add generic behavior here when every store should pass it. Add engine-specific tests in the engine module when behavior depends on RocksDB, FoundationDB or native platform details.

`CareQueryRegressionCases` holds the shared scan and history regressions;
backend test classes supply isolated stores and execute those cases.
`HistoryBoundsRegressionCases` exercises native revision limits, soft-delete and
restoration windows, projections, tied cursors with writes between pages, key
reuse, ordered durable transaction revisions, complete atomic revision payloads,
filtering before limits, rollback and savepoint history across every engine and Remote.
`ModelContractRegressionCases` covers inclusive composite date boundaries on current
and historic cursor pages in both directions, variable string prefixes, complete
list/set/map transaction replacements, atomic empty replacements and retries,
nullable removal, and collection/missing-property Checks with rollback. Run it on
Memory, RocksDB, FoundationDB, IndexedDB and Remote.
Keep regressions with the implementation commit that fixes them. Native RocksDB
iterator-loop regressions also use an iterator budget because a coroutine
timeout cannot interrupt a runnable native scanner.

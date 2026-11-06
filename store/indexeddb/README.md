# Maryk IndexedDB Store

Persistent browser store for Maryk JS and WasmJS apps.

Targets:

- JS browser
- WasmJS browser

The store uses real IndexedDB object stores, keys, key ranges, transactions, and cursor scans. It does not execute requests by loading data into `InMemoryDataStore`.

## Use

Open the store with the data models used by the app:

```kotlin
suspend fun openStore() = IndexedDbDataStore.open(
    databaseName = "my-maryk-store",
    dataModelsById = mapOf(1u to Person),
    keepAllVersions = true,
    keepUpdateHistoryIndex = true,
)
```

Enable history only when the app needs `toVersion`, `getChanges`, `scanChanges`, `getUpdateHistory`, or `scanUpdateHistory` semantics. History adds extra IndexedDB rows for snapshots, indexes, uniques, changes, and update-history scans.

## Documentation

- [Architecture](docs/architecture.md)
- [Storage layout](docs/storage.md)
- [Migrations and browser contexts](docs/migrations.md)

## Main Capabilities

- Current `add`, `change`, `delete`, `get`, `scan`, `getUpdates`, `scanUpdates`.
- Table scans over IndexedDB key cursors.
- Index scans over Maryk-encoded secondary index rows.
- Unique lookups over direct IndexedDB gets.
- Historic `toVersion` get/table/index/unique reads when `keepAllVersions` is enabled.
- `getChanges`, `scanChanges`, `getUpdateHistory`, and `scanUpdateHistory` over durable history rows.
- Sensitive value encryption through the shared Maryk field encryption provider API.
- Browser-native WebCrypto AES-GCM/HMAC-SHA256 provider for JS and WasmJS.
- Metadata-backed startup migration with safe-add handling, index backfill, and
  persisted `Partial`/`Retry` resume state across database reopens.
- JS and WasmJS test coverage through `fake-indexeddb`.

## Important Limits

`getUpdateHistory(key, ...)` and `scanUpdateHistory(...)` return finite,
newest-first pages of record-version entries using `UpdatesResponse.nextCursor`.
Both require `keepAllVersions` and `keepUpdateHistoryIndex`. Filters use values
at each event version before the returned selection is applied. See
[history paging](../../core/docs/query.md#getscan-update-history) for bounds,
continuation, and hard-delete retention limits.

- IndexedDB transactions cannot safely wrap the whole suspend common request processor. Mutations are serialized with Web Locks when available, then written through native batched `readwrite` transactions.
- Without Web Locks, mutations use a renewable lease stored atomically in IndexedDB. `BroadcastChannel` wakes waiting tabs promptly; timed retries cover browsers without it. Every write batch checks the lease in its native transaction, fencing out expired owners.
- Snapshot capture reserves a version in the persisted commit clock under the same writer fence. It includes commits from other tabs before journal polling catches up, and later writers use a greater version.
- A migration returning `Partial` pauses the open after persisting phase, attempt,
  cursor, versions, and message. The next open resumes that phase through
  `MigrationContext.previousState`.
- Migration callbacks can use normal requests after existing journal history has been compacted. Version-update callbacks run after required index backfills and can query the new indexes.
- After a pending migration or schema change in another context, existing contexts
  reject reads and writes and live listeners stop. Reopen with current models.
  See [migration behavior](docs/migrations.md) for callback and resume rules.
- Historic index scans process IndexedDB cursor pages, but still keep latest visible state per indexed row to handle historic tombstones correctly.
- Browser quota, eviction, private browsing behavior, and blocked upgrades are browser operational concerns.

JavaScript `Number` values can canonicalize NaN payloads before Maryk receives
them. Use Wasm when arbitrary supplied NaN bit patterns must survive value and
query round trips; see [numeric identity](docs/storage.md#current-values-and-allocation).

## Tests

Focused checks:

```bash
./gradlew :store:indexeddb:jsNodeTest
./gradlew :store:indexeddb:wasmJsNodeTest
```

Browser checks when available:

```bash
./gradlew :store:indexeddb:jsBrowserTest
./gradlew :store:indexeddb:wasmJsBrowserTest
```

## Sensitive field indexes

Sensitive properties cannot contribute to primary keys or secondary indexes,
including composite and transformed indexes. Use a separate explicit blind-index
field for search. Custom indexes without a dependency contract are rejected when
sensitive properties are present. Existing plaintext key/index bytes from an unsafe
older definition are not erased by startup validation; recover data into a fresh
store with safe definitions and retire old database copies and backups.


## Serializable transaction scopes

IndexedDB previews replay against a private byte-store overlay while the existing Web Lock or fenced writer lease protects Maryk data writers. Commit publishes the final byte batch, revision and receipt in one native readwrite transaction. No browser transaction is held across application suspension. Custom byte-store adapters must implement the atomic writer-fencing contract before advertising support.

The [shared contract](../../core/docs/query.md#serializable-transactions) describes
read-your-own-writes, savepoints, conservative revision conflicts, deadlines and
commit resolution. Query and mutation APIs shipped in 0.12.1 retain their existing
contracts outside an explicit transaction.


SQL reporting, metadata, embedded scalar writes, transactional upsert, bounded
windows, joined mutations, correlated queries and complex map/collection expansion
use the common SQL engine over this store's public requests. See the [SQL guide](../../sql/README.md) for supported
syntax, execution strategies, transaction tooling and resource limits. The shared
SQL conformance contract verifies these features through this adapter.

## Upgrading existing stores

Opening a released store can run a resumable storage-format conversion before
application schema migrations. Stop old writers, retain a recoverable source copy,
and read the [storage upgrade guide](../shared/docs/storage-format-upgrades.md)
before rollout. Some legacy collection/allocation states require authoritative
recovery; do not remove upgrade metadata to bypass a failure.

## Portable backup and restore

Use `backupState` / `restoreState` for current application data without requiring
source history. Use `backup` / `restore` when retained revisions must survive;
that mode requires history on source and target. A state restore target must be
isolated, empty and history-disabled. See the [shared backup guide](../shared/docs/backup-restore.md)
for examples, consistency rules, backend support and native alternatives.

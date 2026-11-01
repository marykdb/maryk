# Query Data

Maryk stores support a range of query actions. Requests can run locally or be serialized and sent to a remote store.

Use Maryk's typed request API as the main API for application reads, writes, history, and sync. Start with the requests in this guide. SQL is an optional layer for reporting, ad hoc queries, and tabular integrations; it is not required to use Maryk stores.

## Mental model

A Maryk query is a typed request object created from a `RootDataModel`.

```kotlin
val request = Person.get(key)
val response = store.execute(request)
```

The same request shape works against every store engine. That is why the CLI, App, Remote Store and embedded stores can share behavior.

Use:

- `Add`, `Change`, `Delete` for writes.
- `Get` when you know keys.
- `Scan` when you need a range, filter, index order or page.
- `GetChanges` / `ScanChanges` for object history.
- `GetUpdateHistory` / `ScanUpdateHistory` for bounded, newest-first historical update entries.
- `GetUpdates` / `ScanUpdates` for chronological sync.
- `executeFlow(...)` for live views.
- `executeAtomic(...)` for a fixed write set with initial-state guards.
- `transaction { ... }` for serializable reads and ordered writes.

Use reference graphs with reads to fetch only the fields you need.

## Common tasks

| Task | Request to start with |
| --- | --- |
| Insert a new object | `Model.add(...)` |
| Update known objects | `Model.change(key.change(...))` |
| Fetch by key | `Model.get(key)` |
| Browse a range or page | `Model.scan(...)` |
| Fetch only a few fields | Add `select` with a reference graph |
| Build a live view | `executeFlow(Model.scanUpdates(...))` |
| Inspect field history | `Model.getChanges(...)` or `Model.scanChanges(...)` |
| Page through record revisions | `Model.getUpdateHistory(key, ...)` |
| Browse historical update entries | `Model.scanUpdateHistory(...)` |

Start with `add`, `get`, and `scan`. Add filters, ordering, reference graphs, and update streams when the access pattern needs them.

## Choosing a write boundary

Atomic execution and transactions share the backend commit engine, but expose
different contracts. Choose a fixed write set when values and guards are known;
choose a transaction when reads determine the writes.

| Requirement | Atomic request | Transaction scope |
| --- | --- | --- |
| Commit several models together | Yes, within one store | Yes, within one store |
| Checks | All guards and preconditions use the initial state | Each operation sees the preceding staged operations |
| Read and decide inside the boundary | Explicit record guards only; earlier application reads are outside it | Get and Scan, including empty predicates, protected by a revision fence |
| Write the same record repeatedly | Once per write set | Separate ordered requests |
| Hard deletion | Unsupported | Supported |
| Savepoints and rollback | No interactive staging lifecycle | Supported before commit submission |
| Recover an uncertain commit | Resubmit the identical request and change-set ID | Resolve the retained transaction's exact commit |

For example, adding an observation and its report with known values fits an atomic
request. Scanning for pending records and changing every match, while protecting
the decision from concurrent insertions, requires a transaction. Atomic version
guards protect selected records; they do not protect an earlier scan's predicate.

Capabilities remain separate for custom adapters: inspect `atomicCapabilities`
and `transactionCapabilities` for the operation you need. Built-in local engines
provide both, and Remote advertises its backend's capabilities. Neither API falls
back to a sequence of ordinary writes when unsupported.

The optional [SQL layer](../../sql/README.md) follows the same distinction:
`execute` uses serializable autocommit, `transaction` supplies an interactive
scope, and `prepareAtomic` retains preselected targets for an atomic write set.

## Basic Query Actions

### Add

Insert model values and inspect the per-record statuses. Ordinary multi-record adds can partially succeed.

See [Add](query/reads-writes.md#add).

### Change

Change fields on existing records. Checks and ordered changes apply within each record mutation; use an explicit write boundary for several records.

See [Change](query/reads-writes.md#change).

### Delete

Soft-delete for later recovery, or hard-delete to erase retained values. A historical version cannot recover erased persistent rows.

See [Delete](query/reads-writes.md#delete).

### Atomic

Commit a known write set and its initial-state guards together. Retain the same request and ID to resolve an uncertain outcome.

See [Atomic](query/atomic.md).

### Get

Fetch known keys, optionally with a projection, filter or retained historical version. Repeated keys preserve request order.

See [Get](query/reads-writes.md#get).

### Scan

Read a bounded page with filters, ordering and an opaque continuation cursor. A suitable key/index or explicit table-scan permission is required.

See [Scan](query/reads-writes.md#scan).

### Get/Scan Changes

Read retained field revisions. Multiple versions require `keepAllVersions`; ScanChanges restricts filters and ordering to immutable paths.

See [Get/Scan Changes](query/history.md#getscan-changes).

### Get/Scan Update History

Page through newest-first record-version entries. Enable both `keepAllVersions` and `keepUpdateHistoryIndex` and continue with `nextCursor`.

See [Get/Scan Update History](query/history.md#getscan-update-history).

### Get/Scan Updates

Read chronological synchronization events or use `executeFlow` for a current live view. Live views and finite history have different delivery and paging semantics.

See [Get/Scan Updates](query/updates.md#getscan-updates).

#### Applying replicated changes

See [replication input validation](query/updates.md#applying-replicated-changes) for
source-version ordering and envelope requirements.

## Serializable transactions

Use a transaction when reads determine the writes, or when a record must be
changed repeatedly. Reads see prior staged writes; commit can report a conflict.
Staged versions are provisional, and uncertain commits require the original handle.

See [Serializable transactions](query/transactions.md) for an example, savepoints,
retry policy, capabilities and recovery. These are distinct from snapshot reads.

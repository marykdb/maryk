# Serializable transactions

Use `store.transaction { tx -> ... }` for an interactive read/write transaction,
or `store.beginTransaction()` when the caller owns the lifecycle. Import these
extensions from `maryk.datastore.shared`. Memory, RocksDB, FoundationDB and the
native IndexedDB byte store implement the same contract; Remote forwards the
backend's transaction capability.

```kotlin
import maryk.datastore.shared.transaction
import maryk.core.query.responses.TransactionStatus

val result = store.transaction { tx ->
    val existing = tx.execute(Person.get(key)).response.values.single()
    tx.execute(Person.change(key.change(Change(nameRef with "Updated"),
        lastVersion = existing.lastVersion)))
    tx.execute(Person.get(key)).response.values.single()
}
if (result.commit.status == TransactionStatus.Committed) {
    useCommittedResult(result.value)
}
```

The scope supports finite `Get`, `Scan`, `Add`, `Change` and `Delete` requests,
including hard deletion and repeated writes to a record. Reads see earlier staged
writes. Point reads, range predicates, empty scans and decisions based on other
models are protected by a datastore-wide revision fence. A concurrent Maryk writer
can cause an explicit `Conflict`, including a writer to an unrelated record.
No changes are published when a preview, validation or conflict check fails.
These conservative conflicts provide serializable isolation without keeping a
native database transaction open while application code suspends.

## Staging and savepoints

Each operation replays the bounded journal in private staging. `executeBatch`
stages a group with one replay. This keeps the implementation common across stores
but means many individual operations cost more than one grouped batch. Savepoints
retain journal positions: `savepoint`, `rollbackTo` and `release` support nested
work. A statement validation/limit failure can recover to an earlier savepoint;
a revision conflict requires a new transaction. `rollback` discards staging.
Read-only scopes use `transaction(readOnly = true)` or
`beginTransaction(readOnly = true)`. A nested scope uses a savepoint.

## Provisional results

`tx.execute` returns a `TransactionStep` marked provisional. Its versions identify
staged records and are rebound when later staged requests use `lastVersion`.
Commit assigns ordered durable versions and returns mutation responses; update
notifications are emitted in journal order after publication. Staged responses,
including SQL RETURNING versions, must not be treated as committed receipts.
The callback value can retain provisional metadata after commit; use the commit
responses when durable mutation versions are needed.
The ordered version range is checked before applying journal members. If it
would overflow the store clock, the transaction aborts without publishing writes.
The scoped callback runs on the shared datastore dispatcher. Ordinary store
`execute` calls inside it join that scope. Replication and subscriptions belong
outside the transaction.

## Atomic requests inside a transaction

An atomic request executed through `tx.executeAtomic(...)`, or through the same
built-in store inside its scoped callback, joins the outer journal. Its guards
are checked before that group's writes against the current staged view. It returns
`AtomicStatus.Staged`; the outer commit determines durability and recovery. The
inner change-set ID does not create an independently committed retry receipt.

## Retries and commit recovery

The callback runs once by default. `transactionWithRetry` explicitly retries the
whole callback after a conflict, up to three attempts with short backoff. Keep
external side effects after a successful commit. `result.transaction` retains the
handle for an uncertain outcome; call `resolveCommit()` on that handle. A submitted
commit cannot be rolled back. Cancellation during a submitted scoped commit exposes
`TransactionCancellationException.transaction` so it can be resolved later.
Persistent stores record a successful receipt in the same native commit as the
writes. Resolution resubmits the exact normalized journal: matching receipts
return the original response before checking stale revisions, while a different
journal with the same committed ID fails explicitly. An absent receipt alone
never establishes rollback. Commit recovery may finish after the original scope
deadline; it always resubmits the original request. If notification delivery fails
after publication, live listeners terminate with an error so they cannot remain
silently stale.

## Capabilities and limits

`store.transactionCapabilities` is null for unsupported adapters. The fixed
scope defaults are 30 seconds, 2,000 journal requests, 1,000 record mutations and
64 savepoints. Request and response payloads are capped at 1 MiB. Both referenced
read keys and cumulative returned read rows are capped at 100,000 and reported as
`maxReadKeys` and `maxReadRows`. Individual Scan requests already enforce a
100,000-row page limit; the transaction also bounds cumulative returned rows.
Memory bounds copied staging nodes to 100,000; RocksDB and IndexedDB
bound staging work to 4 MiB. FoundationDB bounds each native attempt to 4.5 seconds
and 4 MiB, with its 10,000-byte key and 100,000-byte value ceilings. Its native
transaction exists only for a replay attempt. These limits fail explicitly;
no transaction is split into multiple commits. Receipts are retained without
automatic expiry, so they also consume storage (or process memory in Memory).

## Supported operations and ownership

Submitted request values, keys and predicates are copied into the client journal.
Changes to caller-owned collections cannot alter a previously staged operation.
Check-only Change requests are allowed in read-only scopes. Record creation uses
Add; generated replication changes are rejected before staging. A single mutation
request targets each key once. Use separate requests or `executeBatch` for ordered
repeated writes to one key; this prevents same-version changes from skipping a
later write or check. Replication, live
subscriptions and unresolved Remote Inject batches cannot join a transaction.

`AtomicRequest` remains the one-shot convenience for a prepared write set with
initial-state checks. It commits through this transaction engine. Its restricted
write-set shape is distinct from the interactive transaction journal, and does
not promise serializable protection for reads performed before submission.

# Atomic requests

Use `AtomicRequest` when several record mutations form one logical operation. The
existing Add, Change, Delete, and Collect & Inject APIs keep their existing
per-object or sequential behavior.

```kotlin
import maryk.core.query.requests.atomicRequest
import maryk.datastore.shared.executeAtomic

val request = atomicRequest(changeSetId = mutationId) {
    expectVersion(Dossier, dossierKey, dossierVersion)
    val observationKey = add(Observation, observationValues)
    add(Report, reportValues(observationKey))
}
val response = store.executeAtomic(request)
```

The builder performs no I/O. Additions allocate typed keys once, so another
addition can reference them. Retain the constructed request for retries. Direct
construction accepts existing single-record write requests:
`AtomicRequest(id, Model.add(values), OtherModel.change(change))`.

## Commit results

A supported store commits all members together, including values, deletion
markers, indexes, unique ownership, retained history, applicable durable update
logs, and the successful retry receipt. A validation or guard failure returns
`AtomicStatus.Rejected` and no member mutation commits. Its `responses` contains
only the failed member, identified by `failedRequestIndex` and `failedGuard`;
it never reports staged successes as persisted results. `Committed` contains
one response per mutation in request order. A store without the optional
capability returns `Unsupported`; it never falls back to ordinary writes.

## Guards

`expectVersion` adds a read-only concurrency guard. `expect(model, key, Check(...))`
checks property values; an empty `expect` checks physical record existence,
including soft-deleted records. The direct request's `guards` accepts single-record,
check-only Change requests. All guards, embedded `Check` changes, and version
preconditions are evaluated against the initial state, before mutations. They
remain protected through commit. An unchanged guard record is not given a new
version or published as an update.

## Limits

Each mutation addresses one record, and a record can appear in the mutation
list only once. At most 1,000 mutations and 1,000 guards are accepted. The encoded
request and successful response are each limited to 1 MiB; receipts add a
32-byte request digest. FoundationDB bounds affected transaction data to 4 MiB,
with a 4.5-second native timeout setting and a retry limit of 10; each native attempt remains
subject to FoundationDB's transaction lifetime and key/value limits. Receipts
use a manifest and chunks of at most 80,000 bytes in the same transaction.
The same receipt format is used for every request size.
RocksDB and IndexedDB bound staged read/write work to 4 MiB. IndexedDB merges
staged writes with pages of at most 256 stored rows, stopping at the requested
result boundary; a scan may fetch at most 100,000 rows. Memory deep-copies only
targeted records and affected index histories, with a shared budget of 100,000
copied nodes per change set. Untouched records and histories are shared, while
metadata lists are shallow-copied. These budgets never split a change set.

`atomicCapabilities` on an `IsDataStore` reports these fixed ceilings, or `null`
when atomic requests are unsupported. Implementations advertising atomic support
must provide capability metadata. Backend-specific fields are optional; absence
means that a corresponding ceiling is not advertised. A limit report is not a
reservation or a promise that every request below those limits can commit.

Budget rejections populate `AtomicResponse.failure` with `LimitExceeded`, the
stable budget name, `maximum`, and `actual`. Budget names are `mutations`,
`guards`, `requestBytes`, `responseBytes`, `workBytes`, `stagedNodes`, and
`scanRows`. FoundationDB also advertises native storage-cell key/value byte
ceilings and reports `transactionBytes`, `storageKeyBytes`, or `storageValueBytes`
for definitive native size failures. Their `actual` count can be absent when
the native API supplies only an error code. Reusing a committed ID with different contents reports
`ChangeSetMismatch`. Member validation failures retain their existing structured
response and index. `AtomicRequestLimitException` exposes count limits during
construction. Other storage failures may still report `OutcomeUnknown`; a
missing structured limit is not evidence of rollback.

## Supported members

Supported members are Add, Change of an existing record, and soft Delete or
restore. Nested atomic requests, reads/scans, Collect & Inject, hard deletes,
and creation through Change are rejected at construction. Existing model
validation applies. Unique claims are validated in mutation order; an exchange
that requires a temporary unique collision is rejected as a whole. Typed
references retain their existing validation semantics; this API does not add
implicit foreign-key constraints or cascading deletes.

## Retries and uncertain outcomes

A successful receipt is recorded in the same commit as the mutations. Repeating
an identical request with the same `changeSetId` returns the original committed
result without rerunning its now-stale guards. Reusing that ID with different
contents is rejected. Receipts contain a SHA-256 request digest, rather than a
copy of potentially sensitive input values, plus the successful operation
responses. Receipts are retained until the store is destroyed; Memory retains
them only for its open instance. `OutcomeUnknown` reports an unusable or ambiguous execution result. A timeout,
cancellation, or lost connection is not evidence of rollback. Retry the original request with the original ID to
establish its outcome; do not create a new ID for an uncertain retry.

## Versions and visibility

Mutated records share a Maryk HLC version; no-op records retain their previous
version. The HLC is not a cross-writer commit-order token. Atomicity is scoped to
one datastore: one Memory instance, RocksDB database, FoundationDB store root,
or IndexedDB database. Durability follows the engine's existing configuration.
Independent reads may straddle a commit, and existing record-level update
streams and replication retain their existing delivery boundaries. Notifications
are published only after commit, but they are not a grouped cross-model stream.

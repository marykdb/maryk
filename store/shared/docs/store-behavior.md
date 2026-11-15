# Store behavior and lifecycle

Use the [query guide](../../../core/docs/query.md) for request syntax and the
transaction contract. This reference explains behavior that matters when changing
or synchronizing records.

## Decoded-value cache

FoundationDB and RocksDB can reuse decoded values within one request when the
property reference and version match. Datastore paths use an estimated 2 MiB
pool across keys and models where repeated decoding is useful. Single-record
Gets and known scalar reads bypass this pool. Large or unsupported values use
normal decoding. Existing `Cache(...)` callers retain their constructor defaults
and per-key limits.

Repeated Get keys can reuse decoded records from the same snapshot and retry
attempt. Result order, duplicate rows, filters, projections, version metadata and
aggregation contributions are preserved. Each reused result copies built-in
mutable containers. Planning and copy work are bounded; larger or custom graphs
use normal reads. FoundationDB also charges reused rows against its request
materialization budget.

RocksDB and FoundationDB reuse records within their read snapshots. IndexedDB
limits reuse to unfiltered current Gets read together in one native transaction.
Memory enables it for eligible large current full/container Gets without filters
or aggregations. No decoded-value or record cache survives its request.

Cache sizes are portable estimates, not exact heap or response-size limits.
Returned results remain in caller-owned memory after eviction. The pool also
accounts for its metadata and retains per-model and per-key safeguards; unknown
object graphs are not traversed merely to estimate their size. Native caches,
memtables, transactions and responses consume memory separately.

String reads retain strict UTF-8 validation, including optimized decoding from
bounded payloads. These optimizations do not change persisted encodings.

Run `:store:shared:runCachePerformanceProbe --args=request` to compare strict
per-key and pooled behavior, bounded decoding, allocations and retained heap.
Without arguments it retains the earlier entry-count-policy comparison. Probes
validate their results; timings and GC observations are diagnostics, not assertions
or production guarantees.

## Large dossiers and native caches

Keep report records individually addressable, with an index grouping patient and
report ordering fields. Fetch metadata projections in bounded cursor pages, then
load selected report bodies.
Cursor continuation does not keep a database snapshot open between requests.

RocksDB's [block cache](https://github.com/facebook/rocksdb/wiki/Block-Cache)
stores uncompressed database blocks. FoundationDB's client can
[reuse encoded ranges within a transaction](https://forums.foundationdb.org/t/are-range-reads-cached-in-the-c-client/3692).
These layers remain enabled. Maryk record reuse avoids repeated binding calls,
decoding and object construction above them; it does not imply a corresponding
reduction in physical disk reads. Single-pass scalar reads avoid an additional
value cache with no measured reuse. Neither mechanism caches query results across
requests or grows with the entire patient population.

The request pool is separate from native cache, memtable, transaction, response
and process memory budgets. A write-heavy import can temporarily consume more
native memory than steady-state reads. Size engine resources for the workload
and inspect native diagnostics; the request cache is not a total-memory limit.
Avoid opening a separate datastore/native cache per patient when one indexed
store can serve the patient population.

## Mutation bookkeeping

Mutations can skip independent built-in indexes and unrelated unique claims when
the model and changed references prove they are unaffected. Custom dependencies,
nested changes and deletion retain full reconciliation. Validation and mutation
atomicity still apply; no transaction results are cached across requests.

## Write validation and collection boundaries

Write validation runs inside the backend mutation boundary. Typed replacements
validate their declared case, and changes retain predecessor-dependent `final`
and `typeIsFinal` checks. Collection deltas respect the targeted property's
`final` flag; an initialized final map, collection or embedded value also protects
its descendants. Duplicate set additions and empty allocation requests are no-ops.
Supplied values, new map keys and changed collection sizes are validated when
a record is soft-deleted as well. Output-only change records are rejected as
ordinary write commands; replication resolves them before applying stored values.

Collection changes work through map values and active typed cases as well as
model properties. Structural ancestors must exist, and list item references
must stay within the enclosing list. Replacements preserve enclosing collection
sizes and sibling positions. A complex map entry replacement can initialize its
immediate map property; it cannot create missing outer objects or map entries.
Resolved replication allocations can initialize an absent incrementing map and
retain its next local allocation key. Scalar typed index references are read-only;
change the tagged value through its multi type parent. A set item qualifier can
be deleted or assigned its existing value, but cannot receive a different item.

## Deletion and unique values

Undeleting claims the record's current unique values, including changes made while
it was deleted; older historical claims are not restored. Changes validate final
unique ownership, including descendants of replaced or removed collections and
embedded values. Conflicts leave that record and its indexes unchanged.
Sensitive unique values are checked against every configured token candidate.

Persistent stores permanently erase current and historic values on hard deletion,
even with `keepAllVersions`. Use soft deletion when recovery is needed. Memory
retains archived hard-deleted records for history queries during its lifetime.

With `filterSoftDeleted = false`, persistent stores use primary-key table scans
so multiple deleted records with the same formerly unique value remain visible.
Table-scan permissions and minimum key-range bounds still apply. Explicit
secondary-index ordering with deleted records is rejected; use primary-key ordering.

### Persistent unique cells

RocksDB and FoundationDB reconcile affected qualifier ranges, preserving unrelated
encrypted cells. Soft deletion releases owner-matched claims without decrypting
payloads. With retained history, undelete can recover a damaged current cell's
claim from its exact source version; it does not substitute an older value.

## Replication

Submit updates in source-version order and await each call before sending the next.
Stores serialize arrival order; they do not reorder or buffer concurrent deliveries.
Accepted no-op replication changes advance the replay watermark so older events
cannot overwrite acknowledged state. Local no-op requests keep their existing
version and listener behavior.

Replay uses committed values and resolved incrementing-map keys in source operation
order. Source checks and derived index/type notifications are not new mutations.
Missing allocation keys or values reject that version. Replicated creations retain
their final soft-delete state and do not claim active unique indexes until undeleted.

## Checks, typed children and value ownership

Checks read staged values at their position in a change request, including earlier
collection changes. Empty collections differ from absent values. A failed Check
rejects the record mutation.

### Multi-type children

Mutations and checks consult the current staged discriminator to identify the
active case. Inactive children are absent even if older cells remain: writes to
an inactive case are rejected, and inactive deletions leave retained cells untouched.
Activate a case through the multi-type parent before changing its children.
Simple case values are stored inline on that parent; replace it with a `TypedValue`
rather than writing a separate typed child qualifier or simple-case alias.

Stored simple typed cells own immutable wrappers. Retained addition/change events
copy built-in containers and mutation operands so caller reuse cannot rewrite
stored history or later replication events.

## Structural queries and live scans


`Exists` distinguishes a present empty collection or embedded value from an
absent or deleted property. Exact structural comparisons decode the requested
subtree at the query's version. List and set comparisons retain their existing
membership rules; map and embedded-value comparisons retain value equality.
These rules also apply to concrete map entries and list positions.

Persistent stores inspect sensitive scalar presence without decrypting its
payload. Structural comparisons read their selected subtree and required parent
markers, without fetching unrelated sensitive fields. Ancestor collection
projection is temporary and does not rewrite stored counts or qualifiers.

Live indexed scans retain their original ordering boundary when a start record is
changed or deleted. Refills use bounded pages and choose a new record against that
fixed boundary, so another index entry for an already visible record cannot fill
the slot. Records with index entries on both sides of the boundary retain their
eligible position. This can require scanning an earlier index prefix; scanning
stops when later entries cannot improve the refill candidate. Projected cursor
requests retain their validated boundary while internal reads fetch the complete
values needed for ordering.

## Live membership and notification ownership

- Get listeners preserve repeated keys and request order across edits, filter
  changes, deletion and re-creation. Each occurrence receives its own update.
- Incremental addition and change payloads own their built-in collection values,
  so editing one received payload cannot alter another listener's notification.
- Filtered live additions use datastore reads so referenced predicates match
  ordinary queries. Named search predicates, including nested logical filters,
  re-evaluate membership when records change. Snapshot revisions prevent queued
  deltas from replaying changes already included in a fetched addition.
- Geographic predicates track mutable enclosing properties as well as their
  location field, so removing or restoring an optional embedded object updates
  the live result even when its location is final.

## Listener shutdown

Flow completion includes listener cleanup. If store shutdown overlaps cleanup,
its acknowledgement preserves the flow's cancellation or original failure. Other
cleanup failures remain visible. Closing active requests fails them with
`StorageException`.

`closeAllListeners()` completes normally when shutdown overlaps listener removal.
It remains cancellable while the store is open.

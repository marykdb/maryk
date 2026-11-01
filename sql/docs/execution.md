# SQL execution and limits

Start with the [SQL overview](../README.md) for installation and a first query.

## Prepare and stream

Preparation parses and binds the whole statement without reading records. Parameter positions are zero-based in metadata and supplied in textual `?` order. Reuse a prepared query with different values; each execution is independent.

Numeric context infers floating-point or exact parameters where possible. Expressions such as `ABS(?)` accept either numeric category and expose `ANY` output metadata; individual result cells carry their actual numeric type. Numeric constraints also propagate through unary arithmetic and numeric functions in compound and derived outputs. Conflicting nonnumeric constraints fail during preparation; nonnumeric parameter values are rejected before reads. Key and enum parameters retain their domain checks through `CASE`, `COALESCE`, and `NULLIF`.

```kotlin
import kotlinx.coroutines.flow.collect
import maryk.sql.SqlExecutionStatus

val prepared = sql.prepare(
    "SELECT category, SUM(amount) AS total FROM Invoice " +
        "WHERE amount >= ? GROUP BY category ORDER BY total DESC",
)
val execution = prepared.execute(listOf(SqlValue.Int64(100)))

execution.rows.collect { row ->
    println(row["category"] to row["total"])
}
check(execution.awaitCompletion().status == SqlExecutionStatus.SUCCEEDED)
```

An execution's flow has one collector. Call `execute()` again to rerun it. `cancel()` before collection performs no reads. Cancellation during collection cancels its work and leaves the datastore open. Calling `awaitCompletion()` before collecting or cancelling is an execution-state error.

SQL `LIMIT` is successful completion. Stopping consumption early, for example with `take(10)`, is cancellation of an incomplete execution. Rows emitted before an error or cancellation are a partial result. Completion is published after cleanup.

`query()` collects a bounded result for convenience. Use the flow API when the caller can process rows incrementally. Sorting, grouping, joins, and duplicate elimination may still need bounded intermediate storage.

## Consistency and storage engines

The default `SqlConsistency.Current` reads the current store as each request runs. Multi-request queries can observe concurrent changes, including different states across pages and joined sources. It is not a transactional snapshot.

```kotlin
import maryk.sql.SqlConsistency
import maryk.sql.SqlReadOptions

val execution = prepared.execute(
    parameters = listOf(SqlValue.Int64(100)),
    options = SqlReadOptions(consistency = SqlConsistency.Snapshot),
)
```

Snapshot reads require `keepAllVersions` and a store that implements `SnapshotVersionProvider`. The execution captures one authoritative boundary for all its reads. `AtVersion(version)` requires the same support and rejects a future boundary. These are versioned reads, not SQL transactions.

| Store | SQL execution platforms | Automatic snapshot provider |
| --- | --- | --- |
| Memory | All Memory targets | Yes, with retained versions |
| IndexedDB | JS and WasmJS | Yes, with retained versions |
| RocksDB | Its supported JVM, Android, and Native targets | Yes, with retained versions |
| FoundationDB | Its supported JVM and desktop Native targets | Not currently implemented |
| Remote | Its supported JVM and desktop Native targets | Depends on the server's store |

SQL targets the same common platforms as Maryk core. An application must also choose a datastore available on its platform. Server-side authorization and encryption continue through ordinary store requests; a local SQL catalog is not an authorization boundary. When using Remote, deploy a matching server version for request and projection behavior.

## Resource limits

`SqlOptions` provides configurable limits:

| Limit | Default |
| --- | --- |
| Store page size | 256 rows |
| Fetched rows | 100,000 |
| Result rows | 100,000 |
| Logical intermediate buffer | 16 MiB |
| Groups | 10,000 |
| Store requests | 1,000 |
| Execution timeout | 30 seconds |
| Expression work | 10,000,000 steps |

Parsing also bounds SQL text, token count, nesting, parameters, and numeric precision. Exceeding a limit fails the execution with `SqlErrorCode.LIMIT`; it never silently truncates results. A query's own deadline reports `FAILED`, while caller cancellation reports `CANCELLED`. Mutations share one time, request, expression, and buffer budget across selection and the final write; `maxResultRows` also bounds insert rows. Buffer counters estimate retained logical data, not exact platform heap allocation. Fetch counters measure returned records and requests, not physical index entries or network bytes hidden inside a storage engine. Expensive loops check cancellation cooperatively.

Inspect `SqlExecutionSummary` for completion status, fetched/result rows, store requests, expression steps, buffer accounting, the snapshot boundary, and errors. `EXPLAIN ANALYZE` executes the query and reports actual counters.

## Execution strategies and diagnostics

Simple aggregate projections without HAVING use incremental state per group rather
than retaining every input row. DISTINCT aggregate state remains bounded by the
buffer budget. Conditional or complex aggregate projections keep bounded retained
groups to preserve lazy expression errors; this fallback never relaxes limits.
Ordered queries with finite LIMIT and OFFSET, without DISTINCT, retain only the
best LIMIT + OFFSET rows in a stable heap. All candidate expressions still run, so
an optimization cannot hide a failing expression. Oversized offsets fall back to
bounded sorting. Simple, safe record-key ordering can scan in native key order.
A declared scalar property index can also supply ordering when a WHERE predicate
excludes missing values and its type has compatible byte ordering. Text, floating
point, decimal, reversed-storage and transformed index ordering stays local. An indexed path can
scan the index without scanning the table; normal fetch/work budgets still apply.
Sparse indexes and incompatible encodings never define SQL row membership.

A single reference-to-record-key equijoin uses page-sized batched Get requests.
Other joins use bounded local hash or nested-loop execution. No optimizer assumes
that filtering a LEFT JOIN input is equivalent to filtering its result. These
strategies live in common code and use the same public requests on every store.

`prepared.explainPlan()` exposes a structured logical plan. `explain()` remains a
readable rendering; `EXPLAIN ANALYZE` adds execution evidence. Each
`SqlExecutionSummary.accesses` entry reports a table, actual GET/table-scan/index-scan
path, the index encoding when supplied by the store, requests and fetched rows.
Unknown native paths are reported explicitly. Counters describe public store
requests, not hidden physical index visits or exact heap allocation.

### Aggregate execution

Arithmetic around eager aggregate calls can use incremental aggregation, retaining
one accumulator per group instead of all source rows. HAVING queries using safe
COUNT/MIN/MAX calls also stream their group state. Lazy branches, unsafe argument
expressions and HAVING expressions that could hide SUM/AVG failures keep the bounded
reference execution path. These choices preserve NULL handling and error semantics.
Top-N sorting, index ordering and batched reference joins remain available; all
optimizations share the same limits and consistency policy.

Capabilities report `complexCollectionExpansion`, `boundedWindowFrames` and
`joinedMutations`; joinedMutations requires the store's serializable transaction
capability. Memory, RocksDB, FoundationDB, IndexedDB and remote stores use this same
common SQL implementation. Backend historical-read capability remains independent.

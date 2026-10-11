# SQL

Maryk SQL is an optional Kotlin Multiplatform query and mutation layer over Maryk's public datastore requests. Use SQL for reporting, ad hoc queries, and tabular integrations while keeping Maryk models and stores as the source of truth.

The parser, binder, expressions, joins, aggregation, and execution live in `commonMain`. There is no embedded SQLite database, JDBC dependency, or duplicate copy of your data to maintain.

## Reading path

| Need | Start here |
| --- | --- |
| First query | [Set up](#set-up), then [query surface](#query-surface) |
| Consistency and limits | [Storage engines](#consistency-and-storage-engines) and [resource limits](#resource-limits) |
| Writes and recovery | [Choosing a write API](#choosing-a-sql-write-api) and [transactions](#sql-transactions) |
| Reporting | [Windows](#window-functions-and-correlated-queries) and [collection expansion](#collection-expansion) |
| Cross-model writes | [Joined mutations](#joined-mutations) |
| CLI and app sessions | [Interactive transaction tools](#interactive-transaction-tools) |
| Query planning | [Execution strategies and diagnostics](#execution-strategies-and-diagnostics) |

## Set up

In this repository:

```kotlin
commonMain.dependencies {
    implementation(projects.sql)
    implementation(projects.store.memory) // Choose your datastore implementation.
}
```

For published applications, use the same Maryk version as the rest of your application:

```kotlin
implementation("io.maryk:maryk-sql:<maryk-version>")
```

Create the SQL facade around an existing store:

```kotlin
import maryk.sql.MarykSql
import maryk.sql.SqlOptions
import maryk.sql.SqlValue

val sql = MarykSql.create(
    dataStore,
    options = SqlOptions(allowTableScan = true),
)

val result = sql.query(
    "SELECT name, age FROM Person WHERE age >= ? ORDER BY name LIMIT 20",
    parameters = listOf(SqlValue.Int64(18)),
)
for (row in result.rows) {
    println(row["name"])
}

val inserted = sql.execute("INSERT INTO Person (name, age) VALUES ('Ada', 37)")
check(inserted.affectedRows == 1)
```

Unrestricted scans require an explicit `allowTableScan = true`. Configure budgets for your workload. A selective query can still require a scan when the model has no suitable key or index.

## Models, tables, and values

By default, registered root models become tables in the `public` schema. Unquoted identifiers fold ASCII letters to lowercase; double-quoted identifiers preserve case. SQL string literals use single quotes and escape a quote by doubling it.

Provide a catalog to rename tables or expose selected columns:

```kotlin
import maryk.sql.SqlCatalog
import maryk.sql.SqlTable

val catalog = SqlCatalog(listOf(
    SqlTable("people", Person, exposedColumns = setOf("name", "age")),
))
val sql = MarykSql.create(dataStore, catalog, SqlOptions(allowTableScan = true))
```

`__key` is the model's typed record key and `__version` is its current Maryk version. Both system columns remain available when an explicit column set is used. Scalar embedded properties are exposed as dotted paths. Reference values retain their target model identity; reading a reference does not automatically join its target. Enums retain their definition identity and index.

Stored missing properties become SQL `NULL`. The layer reads stored values without applying wrapper conversions or filling model defaults. A default actually stored when the record was created remains a real value. An existing record with absent selected properties still contributes a row.

Use explicit scalar columns for complex models. Scalar collections, embedded record maps and supported nested collection paths can be expanded with `UNNEST`. Collection values have no whole-value comparison/grouping mapping; multi-types, value objects and spatial properties remain unsupported. Unsupported columns are reported explicitly, including when a wildcard would include them.

`SqlValue` represents booleans, signed and unsigned 64-bit integers, arbitrary-size fixed-scale decimals, finite doubles, strings, bytes, dates, times, timestamps, model keys, enums, and `NULL`. `SqlValue.of(...)` converts ordinary scalar Kotlin values. Keys and enums have typed constructors.

Numeric `of(...)` overloads preserve the statically known Kotlin type. JavaScript erases primitive numeric types stored as `Any` or `Number`: the generic overload treats Int-range integral values as integers and other numbers as floating-point values. Use `SqlValue.Int64` or `SqlValue.Float64` explicitly when the numeric category must survive type erasure. Non-finite floating values are rejected.

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

## Query surface

The query layer supports:

- `SELECT` (including explicit `ALL`), expressions and aliases, scalar and qualified wildcards, optional `FROM`, `WHERE`, `DISTINCT`, `ORDER BY`, explicit `NULLS FIRST/LAST`, `LIMIT`, `OFFSET … ROWS`, and `FETCH FIRST/NEXT … ROWS ONLY`. Omitting the fetch count means one row. Parenthesized queries retain their own ordering and pagination.
- Positional parameters, exact literals, temporal literals, arithmetic, concatenation, comparisons, `AND/OR/NOT`, `IS NULL/TRUE/FALSE/UNKNOWN`, `IS DISTINCT FROM`, `IN`, `BETWEEN`, and `LIKE … ESCAPE`.
- Searched and simple `CASE`, `CAST`, `COALESCE`, `NULLIF`, numeric rounding functions, string functions, and `DATE_PART`.
- `COUNT`, `SUM`, `AVG`, `MIN`, `MAX`, `GROUP BY`, `HAVING`, aggregate `DISTINCT`, and aggregate `FILTER (WHERE …)`.
- Nonrecursive CTEs, derived tables, `VALUES`, `UNION`, `INTERSECT`, and `EXCEPT`, with `ALL` where specified.
- Explicit `INNER`, `LEFT`, and `CROSS` joins, with SQL predicate and null-extension semantics.
- Uncorrelated and bounded correlated scalar, `EXISTS`, and `IN` subqueries. A scalar subquery returns `NULL` for no rows and fails for more than one row.
- Window ranking, navigation and aggregate functions, named windows, bounded ROWS frames and numeric-offset RANGE frames.
- Scalar and supported complex collection expansion through UNNEST, with lateral joins and optional ordinality.
- Queryable information_schema table, column and index metadata.
- `EXPLAIN` and `EXPLAIN ANALYZE`, alongside `PreparedSqlQuery.explain()` and execution counters.

`EXISTS` stops at the first result row and skips projections and ordering that do not affect row existence. Distinct values and set-operation operands are still evaluated when needed to determine cardinality, including after an offset.

Examples:

```sql
SELECT category,
       COUNT(*) AS invoices,
       SUM(amount) FILTER (WHERE paid IS TRUE) AS paid_total,
       AVG(amount) AS average
FROM Invoice
GROUP BY category
HAVING COUNT(*) >= 2
ORDER BY paid_total DESC NULLS LAST;
```

```sql
WITH totals AS (
    SELECT customer, SUM(amount) AS total
    FROM Invoice
    GROUP BY customer
)
SELECT p.name, t.total
FROM Person p
LEFT JOIN totals t ON p.__key = t.customer
ORDER BY t.total DESC NULLS LAST;
```

```sql
SELECT name FROM Person
WHERE __key IN (SELECT customer FROM Invoice WHERE amount > 100);
```

`MarykSql.execute(...)` supports bounded INSERT VALUES and INSERT
SELECT, UPDATE (including FROM), soft DELETE (including USING), UNDELETE and DELETE HARD. Each statement is planned,
staged and committed as one serializable autocommit transaction. Predicates may
use the supported SQL expressions and subqueries; WHERE remains mandatory for
updates and deletes. Selected records carry version guards, and the transaction
also protects predicate reads against other writers. Hard deletion includes
soft-deleted records. Remote authorization still sees a DeleteRequest.

Writes support scalar properties, including embedded paths. UPDATE SET field = NULL removes an
optional stored property; INSERT VALUES(NULL) leaves it absent. Required fields
reject NULL. Omitted insert fields remain absent rather than applying model
defaults. Duplicate assignments and non-scalar properties are rejected. Values
must match the SQL column type with lossless exact numeric conversion; use CAST
explicitly to change types or round to stored precision.

```kotlin
import maryk.core.query.responses.AtomicStatus

val changed = sql.execute(
    "UPDATE Person SET nickname = NULL WHERE __key = ? AND __version = ? RETURNING __key",
    listOf(personKey, personVersion),
)
check(changed.response.status == AtomicStatus.Committed)
```

`SqlAtomicWriteResult.affectedRows` counts confirmed mutation targets, and
`response` supplies the structured outcome. A missing key or a version predicate
that no longer matches yields zero rows. Multi-row inserts cannot partly succeed.
RETURNING rows contain durable versions after autocommit. Interactive scopes,
savepoints and the connection-style control statements are described below.
`INSERT … ON CONFLICT` is supported through serializable execution; joined UPDATE … FROM and DELETE … USING are supported through the same serializable execution.

The layer does not provide DDL, JDBC, recursive CTEs, arbitrary lateral derived tables, live SQL subscriptions, or vendor-specific dialect compatibility. Unsupported forms fail explicitly. Schema creation and migrations remain Maryk APIs.

## Numeric and null semantics

Comparisons involving `NULL` produce unknown. `WHERE`, `HAVING`, and `ON` retain only true matches. `NOT IN` with a null candidate can therefore produce unknown. `GROUP BY`, `DISTINCT`, and set operations use SQL null equality.

Floating arithmetic overflow reports `SqlErrorCode.ARITHMETIC`; non-finite results are never emitted.

Exact arithmetic does not pass through `Double`. Signed integers, unsigned integers, and decimals compare by mathematical value. Decimal scales may differ without producing different groups. Mixing approximate floating-point numbers with exact numbers requires an explicit cast.

`SUM` of exact values uses `Decimal`, avoiding the stored property's narrow numeric range. `AVG` and division use decimal scale at least six, retaining greater input scale up to 18, with ties rounded to even. `COUNT` of an empty input is zero; other aggregates return `NULL`. A global aggregate over empty input produces one row; grouped empty input produces none.

Text ordering uses Kotlin's ordinal UTF-16 comparison. `LIKE` and substring character boundaries handle Unicode code points. SQL null ordering defaults to last for ascending and first for descending order. Add an explicit unique tie-breaker when a deterministic total order matters.

Query operands are not writes: a null parameter is legal against a required field, and comparisons outside a property's stored range remain meaningful. Native pushdown is used only when conversion preserves the SQL predicate; remaining predicates execute locally. Text range comparisons stay local because UTF-8 store indexes can order Unicode characters differently from SQL's UTF-16 comparison. Text equality can still use indexes.

Key `IN` lists containing only literals or parameters can use native key access. Computed candidates remain local so an earlier match can skip their evaluation. Native filters and hash joins also keep later predicates local when an earlier computed condition could fail; these queries may require an allowed table scan.

Floating-point comparisons against zero also stay local, preserving SQL equality between positive and negative zero across stores.

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

## Desktop app

The desktop store browser also exposes a **SQL** button beside time travel. It opens
a separate modal with syntax highlighting,
catalog and alias completion, typed parameter inputs, Run/Cancel, Explain, results,
and explicit write mode. See the [app guide](../app/docs/README.md#sql-queries-and-writes).

## CLI

On a connected CLI store:

```text
sql SELECT name FROM Person ORDER BY name LIMIT 20
sql --snapshot SELECT category, SUM(amount) FROM Invoice GROUP BY category
sql --file ./report.sql
sql --write INSERT INTO Person (name, age) VALUES ('Ada', 37)
sql --write UPDATE Person SET nickname = NULL WHERE __key = '<encoded-key>'
```

The CLI preserves SQL quotes and comments. For inline SQL beginning with a line comment, place `--` before the SQL text; files may start with comments directly. Files are bounded to 64 KiB. Output has a tab-separated header and data rows, quotes strings, distinguishes `NULL`, and escapes control characters. It enables table scans with limits of 10,000 result rows and 8 MiB of buffers; use `--no-table-scan` to require a selective native access path. Choose at most one of `--snapshot` and `--to-version`.

The CLI runs queries by default. `--write` enables the bounded mutation surface and prints the commit status, confirmed affected rows and RETURNING rows. It cannot be combined with `--snapshot`, `--to-version`, or `--include-deleted`; use a SQL `__version` predicate to guard a write. Schema changes remain Maryk operations.

## Development and conformance

`sql/src/commonTest` covers parser, binding, values, relational semantics, execution, budgets, and write syntax. The unpublished `:sql:conformance` module supplies one contract to each datastore's tests, including guarded soft and hard deletes, undelete, native projection behavior, paging, exact aggregation, missing values, and historical reads. Remote runs the same contract through HTTP.

```bash
./gradlew :sql:jvmTest
./gradlew :sql:jsNodeTest :sql:wasmJsNodeTest :sql:macosArm64Test
./gradlew :store:memory:jvmTest --tests '*SqlConformanceTest'
```

The existing CI matrix picks up SQL through its aggregate JVM, Native, JS, and WasmJS tasks. A configured target or CI task is not proof that it was executed locally; check test reports for the platform being deployed.

## Choosing a SQL write API

| API | Planning and visibility | Completion and recovery |
| --- | --- | --- |
| `execute` / `prepareMutation(...).execute` | Reads and writes one statement within serializable autocommit; joins an owning scope when present | Durable results after autocommit; resolve the retained transaction on an uncertain commit |
| `transaction` / `beginTransaction` / `session` with BEGIN | Ordered reads and writes see staged changes; protects predicates, including empty selections | Provisional results until the outer commit; savepoints and rollback before submission |
| `prepareAtomic(...).execute` / `executeAtomic` | Selects fixed targets before submission; statements do not see each other's writes | Standalone all-or-nothing commit; retain and retry the same prepared change set |

Use ordinary execution for predicate writes and upsert, a transaction for several
dependent statements, and a prepared atomic change set for a fixed batch whose
keys and guards must remain identical across retries. The two batch contracts
share storage machinery but have different read guarantees. Check
`capabilities.serializableTransactions` for autocommit and scopes, and
`supportsAtomicWrites` / `atomicWriteCapabilities` for prepared atomic batches.
These last two properties are extensions from `maryk.sql`.

## SQL transactions

`sql.transaction { tx -> ... }` combines reads and writes in a serializable scope.
Queries and subsequent writes see earlier staged results, including INSERT SELECT,
predicate UPDATE/DELETE, UNDELETE and DELETE HARD. `WHERE` remains mandatory on
updates and deletes. RETURNING supports the same scalar projections and aliases as
prepared writes. Its staged versions are provisional until commit.

```kotlin
import maryk.sql.transaction
import maryk.core.query.responses.TransactionStatus

val result = sql.transaction { tx ->
    tx.execute("INSERT INTO Person (name) VALUES ('Ada')")
    val point = tx.savepoint()
    tx.execute("UPDATE Person SET name = 'Lin' WHERE name = 'Ada'")
    tx.rollbackTo(point)
    tx.query("SELECT name FROM Person WHERE name = 'Ada'")
}
if (result.commit.status == TransactionStatus.Committed) {
    println(result.value)
}
```

`sql.beginTransaction()` exposes manual commit, rollback and savepoint ownership.
Both entry points accept `readOnly = true`. `tx.query(..., readOptions = ...)`
also retains historical and include-deleted options when the backend supports them. `sql.capabilities.serializableTransactions`
reports support. Query consistency inside the transaction follows the shared
revision fence; outside a transaction, Current query consistency is unchanged.

`sql.session()` supplies sequential connection-style execution. It accepts BEGIN,
BEGIN TRANSACTION, START TRANSACTION, COMMIT, ROLLBACK, SAVEPOINT name,
ROLLBACK TO [SAVEPOINT] name and RELEASE [SAVEPOINT] name. BEGIN/START also accept
READ ONLY or READ WRITE. Queries and mutations join the active session; writes
outside a session transaction use autocommit. Closing a session rolls back work
that has not submitted commit. An uncertain submitted commit remains available
through `session.activeTransaction`; repeat COMMIT or resolve that transaction.
Control statements use the same bounded lexer, including comments and quoted names.
Queries, statements and control operations serialize through the same scope lock.
Read-only scopes reject every mutation statement, including predicates matching
zero rows. FoundationDB currently reports no authoritative SQL history watermark;
its current transaction view remains serializable, while SQL snapshot and AtVersion
reads fail explicitly.

Ordinary `sql.execute` plans and stages one statement before committing it. It
returns confirmed affected rows and RETURNING values with durable versions after
commit. A duplicate, validation failure or conflict publishes no partial statement.
For OutcomeUnknown, `result.transaction` retains the exact commit for
`resolveCommit()`. Cancellation after submission exposes the handle through
`TransactionCancellationException`. Never rerun a fresh statement to resolve an
uncertain commit. The shared 30-second lifetime, 2,000 journal requests, 1,000
mutations, 64 savepoints, 100,000 read keys/rows and backend payload/work budgets apply alongside SQL's
execution limits. There are no weaker isolation settings or silent batch splits.

See the [datastore transaction contract](../core/docs/query.md#serializable-transactions)
for provisional versions, explicit retries, receipt retention and conservative
conflicts with unrelated writes.

## Atomic SQL change sets

Use `prepareAtomic` to plan a bounded change set and `execute()` on the prepared object to commit it. The backend must support Maryk atomic requests; `sql.supportsAtomicWrites` exposes this capability. Ordinary `MarykSql.execute(...)` uses a serializable autocommit transaction, supports general bounded predicates and RETURNING, and returns `SqlAtomicWriteResult`.

```kotlin
import maryk.sql.SqlWriteStatement
import maryk.core.query.responses.AtomicStatus

val prepared = sql.prepareAtomic(
    changeSetId = "import-2026-10-10-001",
    statements = listOf(
        SqlWriteStatement(
            "INSERT INTO Person (name, age) VALUES ('Ada', 37), ('Lin', 42) " +
                "RETURNING __key, __version, name",
        ),
        SqlWriteStatement(
            "UPDATE Invoice SET status = 'reviewed' WHERE status = 'draft' RETURNING __key",
        ),
    ),
)
val result = prepared.execute()
if (result.response.status == AtomicStatus.Committed) {
    println(result.affectedRows)
    result.returning.forEach { statementResult ->
        statementResult.rows.forEach { row -> println(row.values) }
    }
}
```

A single statement can use `prepareAtomic(id, statement, parameters)` or `executeAtomic(id, statement, parameters)`. `executeAtomic` plans and executes once. Retain the prepared object and call its `execute()` again after a lost connection, cancellation, timeout, or `OutcomeUnknown`; replanning can choose different keys, records, values, or guards. The prepared object's `request` exposes the normalized Maryk change set. Successful receipts replay the original operation responses without rerunning stale guards. Retain the prepared object when the caller needs the same `RETURNING` result; the serialized Maryk request alone does not contain those row projections.

Atomic SQL supports:

- Multi-row `INSERT … VALUES` and `INSERT … SELECT`, including bounded query expressions, joins, and CTEs supported by the read engine. Insert queries must produce the same number of columns as the target list.
- Multiple independently parameterized statements across registered models in one change set.
- `UPDATE`, soft `DELETE`, and `UNDELETE` with general SQL `WHERE` predicates. `WHERE` remains mandatory. Table scans still require `SqlOptions(allowTableScan = true)`.
- `RETURNING` scalar expressions, `*`, qualified columns/wildcards, and optional aliases. Aggregates, window functions and subqueries are unsupported in RETURNING. The durable `__version` can only be returned as a direct column; it cannot appear inside a computed returning expression. Updates return assigned values, inserts return planned stored values without filling omitted defaults, and deletes return the selected values before deletion. `__version` is the version reported by the committed member, including an unchanged version for a no-op.

All selected mutation targets carry their observed version into the commit. A stale target, duplicate key, unique conflict, or model-validation failure rejects the entire change set. Rejected, unsupported, and unknown outcomes report zero confirmed affected rows and no returning rows; inspect `result.response` for the actual status and failure information. Returning results correspond to the input statement order; statements without `RETURNING` have empty columns and rows. No post-commit read is used to construct them.

At most 1,000 statements and 1,000 mutated records are accepted in total. Maryk's payload and backend work limits also apply. Limits never split a SQL change set into several commits. A model/key can be mutated only once in the batch; overlapping predicates or repeated writes to the same record are rejected during preparation. Hard deletion is unsupported in atomic SQL.

`sql.atomicWriteCapabilities` reports the statement ceiling and the datastore's
atomic limits; it is `null` for unsupported stores. The new defaults allow larger
batches of small records, while payload and storage-work budgets still bound
expensive changes. SQL execution budgets apply in addition to those ceilings.
Execution-budget and atomic-count failures expose `SqlLimitException.limit`, `maximum`, and
`actual` (when known). Commit-time limits are reported through
`result.response.failure`. The planning deadline does not cover the later commit;
a commit timeout or cancellation can leave an uncertain outcome.

Preparation reads stored data before submitting the change set. Statements do not read each other's staged writes. Target version guards do not protect against newly inserted matching records or changes to other rows used by an insert query or subquery. This API provides all-or-nothing writes over the planned targets, not a general read/write transaction or serializable predicate protection. Empty selections produce a successful zero-row result, with `request == null` and no stored receipt.

`ON CONFLICT` requires ordinary serializable SQL execution and is rejected by prepared atomic change sets. Interactive transaction scopes support repeated writes and hard deletion.

Joined UPDATE FROM and DELETE USING also require serializable execution and are
rejected by prepared atomic change sets. Prefer `tx.execute` for statements that
depend on staged data. Executing a retained atomic change set inside the same
store's scoped transaction stages it in the outer journal: `AtomicStatus.Staged`,
its affected count and RETURNING values are provisional. Resolve the outer
transaction after an uncertain commit; the inner change-set ID has no independent
receipt. Reads performed during preparation outside that scope remain unprotected.


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

## Catalog discovery

The reserved `information_schema` schema is generated from the exposed immutable
catalog and needs no datastore reads:

```sql
SELECT table_schema, table_name, model_name FROM information_schema.tables;
SELECT column_name, data_type, is_nullable, is_writable, is_unique
FROM information_schema.columns WHERE table_name = 'Person';
SELECT is_primary, encoding FROM information_schema.indexes
WHERE table_name = 'Person';
```

`tables` also contains table_type. `columns` contains table_schema, table_name,
ordinal_position and is_system. `indexes` contains table_schema, table_name and
index_ordinal; ordinal zero describes the record-key encoding. Index encodings
preserve the Maryk definition identity, including transformed keys. The catalog
reflects locally exposed columns and models and does not replace Remote server
authorization. Feature capabilities report metadata, windows, correlation,
collection expansion, scalar returning expressions, embedded writes and
transactional upsert alongside store consistency support.

## Prepared mutations, embedded fields and upsert

`sql.prepareMutation(statement)` parses and binds without reading records. Its
parameter metadata is available immediately; execution reads current data and
starts autocommit, or joins the owning SQL transaction. Preparing is different
from retaining a previously selected atomic change set. Each execution of a
prepared mutation is a new write. Resolve an uncertain outcome through its exact
returned transaction handle, rather than executing the mutation again.

```kotlin
import maryk.sql.prepareMutation

val prepared = sql.prepareMutation(
    "UPDATE Person SET address.city = ? WHERE __key = ? RETURNING address.city",
)
val changed = prepared.execute(listOf(SqlValue.Text("Utrecht"), personKey))
```

Embedded scalar paths work in INSERT and UPDATE. Missing embedded parents are
created only when non-NULL leaf values are supplied. Updating an existing parent
preserves its other fields, including complex fields. Required leaves reject NULL;
optional leaves can be cleared. Model validation, final fields, uniqueness and
version guards still apply. No omitted model defaults are filled implicitly. Recursive embedded definitions
stop automatic flattening at the cycle and expose that boundary as non-scalar,
so other columns remain usable without generating an infinite catalog.

```sql
INSERT INTO Person (email, name) VALUES ('ada@example.test', 'Ada')
ON CONFLICT (email) DO UPDATE SET name = excluded.name
RETURNING __key, UPPER(name) AS display_name, __version;
```

A conflict target must be `__key`, the complete untransformed property key in its
defined order, or one unique scalar property. `DO NOTHING` skips matching rows;
`DO UPDATE SET` can read existing fields and the proposed `excluded` fields.
NULL unique values do not match each other. Repeated input targets are skipped by
DO NOTHING; DO UPDATE rejects targeting a record twice within one statement.
`excluded.__version` is unavailable: an incoming row has no committed version.
Use target `__version` when an assignment needs the observed stored version.

Other uniqueness violations reject the complete statement. Selection and writes
share the transaction's revision fence, so a concurrent insertion cannot silently
escape conflict protection. Conflict selection observes live rows; an occupied
soft-deleted key can still reject insertion. Use UNDELETE explicitly when restoring
such a record. There is no automatic resurrection or overwrite of another key.

## Window functions and correlated queries

Supported windows are ROW_NUMBER, RANK, DENSE_RANK, NTILE, LAG, LEAD,
FIRST_VALUE, LAST_VALUE, NTH_VALUE, COUNT, SUM, AVG, MIN and MAX. OVER accepts
PARTITION BY and ORDER BY with ordinary SQL null placement. Named definitions use
`WINDOW name AS (...)` and `OVER name`; definitions belong to one query scope.
Window inheritance and frame exclusion are not supported.

LAG/LEAD accept a constant nonnegative integer offset and a scalar default.
NTILE requires a positive constant integer bucket count; larger buckets come first.
NTH_VALUE uses a positive one-based constant integer index. Value functions respect
the frame and include NULL values; an empty frame or missing nth element returns NULL.

Without ordering, the default aggregate frame covers the full partition. With
ordering, it includes peers through the current ordering value. ROWS frames accept
CURRENT ROW, UNBOUNDED PRECEDING/FOLLOWING and constant nonnegative integer offsets.
RANGE frames support peer boundaries and unbounded boundaries; numeric offsets
require exactly one numeric ordering expression. RANGE offsets may be fractional;
offsets outside the floating numeric range report a structured arithmetic error;
temporal INTERVAL offsets are not supported. Descending order reverses offset direction.
Invalid boundary categories are rejected; a valid frame outside the partition is empty.
Parameters are allowed for constant offsets and bucket counts.

Full and cumulative aggregate frames use incremental state. Other frames evaluate
only their member rows and charge each visit to the expression work budget, so wide
sliding frames can reach that budget. Partition count uses the normal group budget;
row, expression, memory and time budgets apply throughout execution.

```sql
SELECT customer, amount,
       ROW_NUMBER() OVER (PARTITION BY customer ORDER BY amount DESC) AS rank,
       SUM(amount) OVER (PARTITION BY customer ORDER BY issued
                        ROWS UNBOUNDED PRECEDING) AS running_total
FROM Invoice;
```

Windows run after WHERE and before final DISTINCT, ordering and pagination. They
are unavailable in WHERE, HAVING, mutation assignments and RETURNING. To apply a
window to grouped output, put the aggregate in a CTE or derived relation first.
There is no promise of a stable ordering among tied rows unless the window order
contains a distinguishing key.

Correlated scalar, EXISTS and IN subqueries retain their lexical outer bindings.
Only uncorrelated results are cached, including CTEs and set-operation operands.
Join optimizations use local inputs; outer-scope join predicates retain their lexical
row through evaluation. Correlated evaluations share execution
budgets and the same snapshot or transaction fence; safe outer key predicates can
use native lookups. Scalar cardinality and SQL NULL/NOT IN rules remain unchanged.
Correlation can cost many requests and therefore fails explicitly on budget
exhaustion. Arbitrary correlated derived tables remain unsupported.

## Collection expansion

```sql
SELECT p.__key, e.ordinality, e.value
FROM Person p CROSS JOIN UNNEST(p.aliases) WITH ORDINALITY e;

SELECT p.__key, e.entry_key, e.value
FROM Person p LEFT JOIN UNNEST(p.labels) e ON TRUE;
```

UNNEST accepts scalar Maryk lists, sets, maps and incrementing maps, including
embedded collection paths, or an explicit `SqlValue.Collection` parameter. Scalar
entries expose `value`; maps also expose their typed `entry_key`, which is NULL for
lists and sets. WITH ORDINALITY adds one-based ordinality. Lists retain stored order;
sets and maps do not promise an ordering. Keep the parent __key and map entry_key
when identifying expanded records. List ordinality is not a persistent element ID.

Maps of embedded records expose typed flattened fields such as `e.value.label`.
Collections within those records and maps whose values are collections can feed
another UNNEST. SQL uses the model's types, including reference and enum domains:

```sql
SELECT p.__key, e.entry_key, e.value.label, child.value
FROM Person p
CROSS JOIN UNNEST(p.details) e
LEFT JOIN UNNEST(e.value.children) child ON TRUE;

SELECT p.__key, groupEntry.entry_key, member.value
FROM Person p
CROSS JOIN UNNEST(p.groups) groupEntry
CROSS JOIN UNNEST(groupEntry.value) member;
```

Missing and empty collections produce zero rows in a CROSS/INNER join. LEFT JOIN
preserves the parent with NULL extended columns. Expansion is lateral to preceding
sources and shares all normal budgets. Maryk storage does not support complex
list/set elements; these are rejected during binding. Unsupported opaque or
recursive record boundaries are also rejected. Collection nesting is bounded at
32 levels in both schema inspection and value traversal, and 100,000 visited scalar/collection cells per value, including map keys
and embedded fields, in addition to query relation and memory/work limits. This
also bounds repeated traversal of shared nested parameter graphs.

Explicit collection parameters may contain nested collections; their entry keys
must remain scalar, and only maps carry non-NULL keys. Parameters expose ANY
value/key metadata because element types arrive at execution. Nested parameter
collections can feed another UNNEST. Typed record field expansion requires a model
schema. Grouping/comparing collections as a whole remains unsupported. CLI and app
queries use these same model collection rules; collection parameters use the Kotlin API.

## Joined mutations

```sql
UPDATE Invoice AS i SET customerName = c.name
FROM Customer c WHERE i.customer = c.__key
RETURNING i.__key, i.customerName;

DELETE FROM Invoice AS i USING Customer c
WHERE i.customer = c.__key AND c.inactive = TRUE
RETURNING i.__key;
```

Joined mutations run in the same serializable transaction as their reads. They
support source joins, derived queries and UNNEST through the shared query engine.
WHERE remains required. Assignments may read target and source scalar columns;
RETURNING refers only to the target and reports its resulting values. Qualify
ambiguous columns. More than one source match for a target rejects the statement
before staging writes, even when all matches would assign identical values. No
source match changes no record. Normal mutation, fetched-row, request, memory and
time budgets apply; limits never split a statement into partial commits.

Prepared mutations and interactive scopes support these forms. The immutable
`prepareAtomic` API rejects joined mutations because cross-model selection needs
the serializable transaction read fence. Read-only transactions reject them.
`DELETE HARD FROM … USING …` is supported by transactional SQL and follows the
same target/version rules; hard-delete selection includes soft-deleted records in
its sources. SQL does not add schema constraints or implicit defaults.

## Interactive transaction tools

The CLI retains one SQL session per connected store:

```text
sql --write BEGIN
sql --write INSERT INTO Person (name) VALUES ('Ada')
sql SELECT name FROM Person
sql --write SAVEPOINT draft
sql --write UPDATE Person SET name = 'Lin' WHERE name = 'Ada'
sql --write ROLLBACK TO draft
sql --write COMMIT
```

Transaction commands require an interactive connection and --write. The scan
policy chosen at BEGIN remains fixed until the transaction ends. Disconnect rolls
back staging; uncertain commits must be resolved before changing connections.
The desktop console accepts the same commands in write mode, shows active
transaction state, and retains the session while the modal is closed. Disconnect
rolls back staging and refuses to discard an unresolved or submitting write.

`tx.prepare(statement).execute(...).rows` provides prepared streaming reads within
a SQL transaction. Its single collector owns the SQL scope lock until cleanup;
commit, rollback and savepoints wait for that collection. Cancellation releases
the lock and leaves the transaction available. `tx.prepareMutation(...)` provides
prepared scoped writes under the same lock. The transaction lifetime continues
to apply while results are consumed.

## Aggregate execution improvements

Arithmetic around eager aggregate calls can use incremental aggregation, retaining
one accumulator per group instead of all source rows. HAVING queries using safe
COUNT/MIN/MAX calls also stream their group state. Lazy branches, unsafe argument
expressions and HAVING expressions that could hide SUM/AVG failures keep the bounded
reference execution path. These choices preserve NULL handling and error semantics.
Top-N sorting, index ordering and batched reference joins remain available; all
optimizations share the same limits and consistency policy. Defaults are unchanged.

Capabilities report `complexCollectionExpansion`, `boundedWindowFrames` and
`joinedMutations`; joinedMutations requires the store's serializable transaction
capability. Memory, RocksDB, FoundationDB, IndexedDB and remote stores use this same
common SQL implementation. Backend historical-read capability remains independent.

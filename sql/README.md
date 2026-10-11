# SQL

Maryk SQL is an optional Kotlin Multiplatform query layer over Maryk's public datastore requests. Use SQL for reporting, ad hoc queries, and tabular integrations while keeping Maryk models and stores as the source of truth.

The parser, binder, expressions, joins, aggregation, and execution live in `commonMain`. There is no embedded SQLite database, JDBC dependency, or duplicate copy of your data to maintain.

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

Use explicit scalar columns for models with lists, sets, maps, multi-types, embedded objects, value objects, or spatial properties that have no scalar SQL mapping. Unsupported columns are reported explicitly, including when a wildcard would include them.

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

The first version supports:

- `SELECT` (including explicit `ALL`), expressions and aliases, scalar and qualified wildcards, optional `FROM`, `WHERE`, `DISTINCT`, `ORDER BY`, explicit `NULLS FIRST/LAST`, `LIMIT`, `OFFSET … ROWS`, and `FETCH FIRST/NEXT … ROWS ONLY`. Omitting the fetch count means one row. Parenthesized queries retain their own ordering and pagination.
- Positional parameters, exact literals, temporal literals, arithmetic, concatenation, comparisons, `AND/OR/NOT`, `IS NULL/TRUE/FALSE/UNKNOWN`, `IS DISTINCT FROM`, `IN`, `BETWEEN`, and `LIKE … ESCAPE`.
- Searched and simple `CASE`, `CAST`, `COALESCE`, `NULLIF`, numeric rounding functions, string functions, and `DATE_PART`.
- `COUNT`, `SUM`, `AVG`, `MIN`, `MAX`, `GROUP BY`, `HAVING`, aggregate `DISTINCT`, and aggregate `FILTER (WHERE …)`.
- Nonrecursive CTEs, derived tables, `VALUES`, `UNION`, `INTERSECT`, and `EXCEPT`, with `ALL` where specified.
- Explicit `INNER`, `LEFT`, and `CROSS` joins, with SQL predicate and null-extension semantics.
- Uncorrelated scalar, `EXISTS`, and `IN` subqueries. A scalar subquery returns `NULL` for no rows and fails for more than one row.
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

`MarykSql.execute(...)` supports bounded single-table INSERT VALUES and INSERT
SELECT, UPDATE, soft DELETE, UNDELETE and DELETE HARD. Each statement is planned,
staged and committed as one serializable autocommit transaction. Predicates may
use the supported SQL expressions and subqueries; WHERE remains mandatory for
updates and deletes. Selected records carry version guards, and the transaction
also protects predicate reads against other writers. Hard deletion includes
soft-deleted records. Remote authorization still sees a DeleteRequest.

Writes support direct scalar properties. UPDATE SET field = NULL removes an
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
UPSERT and join-target mutations remain unsupported.

The layer does not provide DDL, JDBC, recursive CTEs, correlated subqueries, window functions, live SQL subscriptions, or vendor-specific dialect compatibility. Unsupported forms fail explicitly. Schema creation and migrations remain Maryk APIs.

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

## SQL transactions

`sql.transaction { tx -> ... }` combines reads and writes in a serializable scope.
Queries and subsequent writes see earlier staged results, including INSERT SELECT,
predicate UPDATE/DELETE, UNDELETE and DELETE HARD. `WHERE` remains mandatory on
updates and deletes. RETURNING supports the same scalar columns and aliases as
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

Use `prepareAtomic` to plan a bounded change set and `execute()` on the prepared object to commit it. The backend must support Maryk atomic requests; `sql.supportsAtomicWrites` exposes this capability. Ordinary `MarykSql.execute(...)` uses a serializable autocommit transaction, supports general bounded predicates and RETURNING, and returns `SqlAtomicWriteResult`. The unshipped per-record and single-key execution paths have been removed.

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
- `RETURNING` scalar columns, `*`, qualified columns/wildcards, and optional aliases. Arbitrary returning expressions and subqueries are unsupported. Updates return assigned values, inserts return planned stored values without filling omitted defaults, and deletes return the selected values before deletion. `__version` is the version reported by the committed member, including an unchanged version for a no-op.

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

`UPSERT` and `ON CONFLICT` remain unsupported. Interactive transaction scopes support repeated writes and hard deletion.

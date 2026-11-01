# SQL

Maryk SQL is an optional Kotlin Multiplatform query and mutation layer over Maryk's public datastore requests. Use SQL for reporting, ad hoc queries, and tabular integrations while keeping Maryk models and stores as the source of truth.

For application reads, writes, history, and sync, use Maryk's [typed request API](../core/docs/query.md) as your main API. SQL is not required to use Maryk stores; add the `maryk-sql` dependency only when your application needs SQL.

The parser, binder, expressions, joins, aggregation, and execution live in `commonMain`. There is no embedded SQLite database, JDBC dependency, or duplicate copy of your data to maintain.

## Reading path

- [Query language, values and reporting](docs/queries.md).
- [Writes, transactions and recovery](docs/writes.md).
- [Streaming, consistency, limits and diagnostics](docs/execution.md).

## Set up

These docs describe the current source branch. For published artifacts, read the
documentation at the matching release tag and use the same version for all Maryk
modules. This example chooses Memory for a first run; replace it with your
application's store when integrating SQL.

```kotlin
implementation("io.maryk:maryk-sql:<maryk-version>")
implementation("io.maryk:maryk-memory:<maryk-version>")
```

The following JVM entry point defines its model, opens a store, inserts a record,
and queries it. Unrestricted scans require explicit `allowTableScan = true`.

```kotlin
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.SInt32
import maryk.datastore.memory.InMemoryDataStore
import maryk.sql.MarykSql
import maryk.sql.SqlOptions
import maryk.sql.SqlValue

object Person : RootDataModel<Person>() {
    val name by string(index = 1u)
    val age by number(index = 2u, type = SInt32)
}

suspend fun main() {
    val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to Person))
    try {
        val sql = MarykSql.create(store, options = SqlOptions(allowTableScan = true))
        val inserted = sql.execute("INSERT INTO Person (name, age) VALUES ('Ada', 37)")
        check(inserted.affectedRows == 1)

        val result = sql.query(
            "SELECT name, age FROM Person WHERE age >= ? ORDER BY name LIMIT 20",
            parameters = listOf(SqlValue.Int64(18)),
        )
        check(result.rows.single()["name"] == SqlValue.Text("Ada"))
        println(result.rows.single()["name"])
    } finally {
        store.close()
    }
}
```

The example prints `Text(value=Ada)`. It uses a fresh in-memory store on each run.

Configure [execution budgets](docs/execution.md#resource-limits) for the workload.
A selective query can still require a scan when the model has no suitable key or index.

## Models, tables, and values

Registered models become tables. Missing stored values become SQL `NULL`; reads do not synthesize model defaults. See the reference for catalogs, scalar types and exposed columns.

See [models, tables, and values](docs/queries.md#models-tables-and-values).

## Prepare and stream

Reuse prepared queries with typed parameters and consume rows as a bounded flow. Each execution has one collector; stopping early is cancellation of a partial result.

See [prepare and stream](docs/execution.md#prepare-and-stream).

## Query surface

Use SELECT, filters, joins, aggregates, CTEs, subqueries, windows and supported UNNEST paths. DDL, JDBC, recursive CTEs and live SQL subscriptions are not provided.

See [query surface](docs/queries.md#query-surface).

## Numeric and null semantics

Exact arithmetic stays exact; mixing exact and floating types requires a cast. SQL three-valued null logic applies to predicates, joins and grouping.

See [numeric and null semantics](docs/queries.md#numeric-and-null-semantics).

## Consistency and storage engines

Current reads can observe changes between requests. Snapshot and AtVersion reads require retained history and an authoritative snapshot provider; they are distinct from serializable write transactions.

See [consistency and storage engines](docs/execution.md#consistency-and-storage-engines).

## Resource limits

Execution is bounded by fetched/result rows, memory, groups, requests, work and time. Defaults include 100,000 fetched/result rows and a 30-second timeout. Limits fail explicitly rather than truncating results.

See [resource limits](docs/execution.md#resource-limits).

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

For in-repository examples, use the Gradle project dependencies:

```kotlin
commonMain.dependencies {
    implementation(projects.sql)
    implementation(projects.store.memory)
}
```


`sql/src/commonTest` covers parser, binding, values, relational semantics, execution, budgets, and write syntax. The unpublished `:sql:conformance` module supplies one contract to each datastore's tests, including guarded soft and hard deletes, undelete, native projection behavior, paging, exact aggregation, missing values, and historical reads. Remote runs the same contract through HTTP.

```bash
./gradlew :sql:jvmTest
./gradlew :sql:jsNodeTest :sql:wasmJsNodeTest :sql:macosArm64Test
./gradlew :store:memory:jvmTest --tests '*SqlConformanceTest'
```

The existing CI matrix picks up SQL through its aggregate JVM, Native, JS, and WasmJS tasks. A configured target or CI task is not proof that it was executed locally; check test reports for the platform being deployed.

## Choosing a SQL write API

Use `execute` for a statement in serializable autocommit, `transaction` for dependent reads and writes, and `prepareAtomic` for a fixed retryable write set. These entry points have different read guarantees.

See [choosing a sql write api](docs/writes.md#choosing-a-sql-write-api).

## SQL transactions

Statements see earlier staged writes. Results remain provisional until commit. Retain the transaction handle to resolve an uncertain commit; do not rerun a fresh statement to establish its outcome.

See [sql transactions](docs/writes.md#sql-transactions).

## Atomic SQL change sets

Prepared atomic sets retain selected targets and guards. Keep the original set and ID for retries; preparation outside a transaction does not protect predicate reads.

See [atomic sql change sets](docs/writes.md#atomic-sql-change-sets).

## Execution strategies and diagnostics

Use EXPLAIN and execution summaries to inspect access paths, counters and budgets. Optimizations preserve query results, error behavior and resource limits.

See [execution strategies and diagnostics](docs/execution.md#execution-strategies-and-diagnostics).

## Catalog discovery

Query `information_schema` for exposed tables, columns and indexes. This local catalog is not an authorization boundary.

See [catalog discovery](docs/queries.md#catalog-discovery).

## Prepared mutations, embedded fields and upsert

Prepare scalar writes, update embedded paths and use ON CONFLICT within serializable execution. Model validation, final fields, uniqueness and required-value constraints remain active.

See [prepared mutations, embedded fields and upsert](docs/writes.md#prepared-mutations-embedded-fields-and-upsert).

## Window functions and correlated queries

Use bounded ranking, navigation and aggregate windows, plus supported correlated scalar, EXISTS and IN subqueries. They share the execution budgets.

See [window functions and correlated queries](docs/queries.md#window-functions-and-correlated-queries).

## Collection expansion

UNNEST expands scalar collections and supported map/embedded paths. The reference defines supported shapes, ordering, empty collections and nesting limits.

See [collection expansion](docs/queries.md#collection-expansion).

## Joined mutations

UPDATE FROM and DELETE USING run with their selection reads in one serializable transaction. Multiple source matches for a target reject the statement.

See [joined mutations](docs/writes.md#joined-mutations).

## Interactive transaction tools

The CLI and desktop console support BEGIN, COMMIT, ROLLBACK and savepoints. Resolve uncertain commits before changing connections.

See [interactive transaction tools](docs/writes.md#interactive-transaction-tools).

## Aggregate execution improvements

Aggregate execution uses bounded incremental state where supported. See execution strategies for fallback behavior and diagnostics.

See [aggregate execution improvements](docs/execution.md#aggregate-execution).

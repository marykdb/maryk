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

## Capabilities and limits

Registered models become tables. Use bounded SELECTs, joins, aggregates, windows,
subqueries and supported UNNEST paths for reporting; use `execute`, `transaction`
or `prepareAtomic` for the appropriate write boundary. DDL, JDBC, recursive CTEs
and live SQL subscriptions are not provided.

Missing stored values become SQL `NULL`; reads do not synthesize model defaults.
Current reads may observe changes between requests. Snapshot reads require
retained history and a supporting snapshot provider; they are distinct from
serializable write transactions. Resource limits fail explicitly rather than
silently truncating results. Configure them for the workload.

| Task | Reference |
| --- | --- |
| Model/table mapping, types, NULLs and catalogs | [Queries and values](docs/queries.md#models-tables-and-values) |
| Joins, windows, subqueries and collection expansion | [Query surface](docs/queries.md#query-surface) |
| Choose a write API; use transactions, upsert, joined mutations or commit recovery | [Writes and transactions](docs/writes.md) |
| Prepare and stream queries; configure snapshots, budgets and diagnostics | [Execution and limits](docs/execution.md) |

## CLI and desktop tools

Use `sql SELECT ...` or `sql --file report.sql` on a connected CLI store. Writes
require `sql --write ...`; interactive sessions support transactions and
savepoints. The desktop App provides a SQL console with parameters, results,
Explain and explicit write mode.

See the [CLI command reference](../cli/docs/commands.md#sql) and
[desktop SQL guide](../app/docs/README.md#sql-queries-and-writes) for options,
limits and recovery behavior.

## Development and conformance

For in-repository examples, use the Gradle project dependencies:

```kotlin
commonMain.dependencies {
    implementation(projects.sql)
    implementation(projects.store.memory)
}
```

`sql/src/commonTest` covers parsing, binding, expressions, execution and budgets.
The unpublished `:sql:conformance` module checks query and mutation behavior
across stores, including Remote over HTTP.

```bash
./gradlew :sql:jvmTest
./gradlew :sql:jsNodeTest :sql:wasmJsNodeTest :sql:macosArm64Test
./gradlew :store:memory:jvmTest --tests '*SqlConformanceTest'
```

Check conformance on the storage engine and target platform being deployed.

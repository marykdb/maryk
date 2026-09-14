package maryk.sql

import kotlinx.coroutines.flow.collect
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.SnapshotVersionProvider
import maryk.sql.syntax.SqlParseException
import maryk.sql.syntax.SqlParser

data class SqlOptions(
    val allowTableScan: Boolean = false,
    val pageSize: Int = 256,
    val maxFetchedRows: Long = 100_000,
    val maxResultRows: Long = 100_000,
    val maxBufferedBytes: Long = 16L * 1024 * 1024,
    val maxGroups: Int = 10_000,
    val maxRequests: Int = 1_000,
    val maxExecutionMillis: Long = 30_000,
    val maxExpressionSteps: Long = 10_000_000,
    val defaultSchema: String = "public",
) {
    init {
        require(pageSize in 1..100_000)
        require(maxFetchedRows > 0 && maxResultRows > 0 && maxBufferedBytes > 0)
        require(maxGroups > 0 && maxRequests > 0 && maxExecutionMillis > 0 && maxExpressionSteps > 0)
    }
}

sealed interface SqlConsistency {
    data object Current : SqlConsistency
    data object Snapshot : SqlConsistency
    data class AtVersion(val version: ULong) : SqlConsistency
}

data class SqlReadOptions(
    val consistency: SqlConsistency = SqlConsistency.Current,
    val filterSoftDeleted: Boolean = true,
)

data class SqlResult(val columns: List<SqlColumn>, val rows: List<SqlRow>)

data class SqlCapabilities(
    /** Historical SQL requires both retained versions and a store-authoritative watermark provider. */
    val historicalReads: Boolean,
    val correlatedSubqueries: Boolean = false,
    val maxPlanNodes: Int = 256,
    val maxRelationDepth: Int = 64,
    val currentReadsAreWeaklyConsistent: Boolean = true,
)

class MarykSql private constructor(
    internal val dataStore: IsDataStore,
    val catalog: SqlCatalog,
    val options: SqlOptions,
) {
    val capabilities = SqlCapabilities(dataStore.keepAllVersions && dataStore is SnapshotVersionProvider)

    /** Parse and bind without reading records. A prepared query can be executed repeatedly. */
    fun prepare(statement: String): PreparedSqlQuery {
        val parsed = try { SqlParser(statement).parse() } catch (error: SqlParseException) {
            val message = error.message ?: "Invalid SQL"
            val limit = listOf("Token limit of", "AST depth limit of", "AST node limit of", "Parameter limit of", "Nesting depth limit of").any { message.startsWith(it) }
            throw SqlException(if (limit) SqlErrorCode.LIMIT else SqlErrorCode.SYNTAX, message, error.position, error)
        }
        val bound = SqlBinder(catalog, options).bind(parsed)
        val seen = mutableSetOf<BoundQuery>()
        fun checkTables(query: BoundQuery) {
            if (!seen.add(query)) return
            query.sources.mapNotNull { it.table }.forEach { table ->
                if (dataStore.dataModelsById.values.none { it === table.model }) throw SqlException(SqlErrorCode.BINDING, "Table '${table.name}' is not registered in this data store")
            }
            query.children.forEach(::checkTables)
        }
        checkTables(bound)
        return PreparedSqlQuery(this, bound)
    }

    /** Collect a bounded result. For incremental consumption use prepare(...).execute().rows. */
    suspend fun query(statement: String, parameters: List<SqlValue> = emptyList(), readOptions: SqlReadOptions = SqlReadOptions()): SqlResult {
        val execution = prepare(statement).execute(parameters, readOptions)
        val rows = mutableListOf<SqlRow>()
        execution.rows.collect { row ->
            execution.retainCollectedRow(row)
            rows += row
        }
        return SqlResult(execution.columns, rows)
    }

    /** Execute a bounded single-table INSERT, UPDATE, or soft DELETE. */
    suspend fun execute(statement: String, parameters: List<SqlValue> = emptyList()): SqlWriteResult =
        SqlWriter(this).execute(statement, parameters)

    companion object {
        fun create(dataStore: IsDataStore, catalog: SqlCatalog = SqlCatalog.from(dataStore), options: SqlOptions = SqlOptions()): MarykSql =
            MarykSql(dataStore, catalog, options)
    }
}

class PreparedSqlQuery internal constructor(private val sql: MarykSql, internal val plan: BoundQuery) {
    val columns: List<SqlColumn> = if (plan.query.explain) listOf(SqlColumn("plan", SqlType.TEXT, false)) else plan.columns
    val parameters: List<SqlParameter> = List(plan.query.parameterCount) { SqlParameter(it, plan.bindingState.parameterTypes[it] ?: SqlType.ANY) }

    fun explain(): String = buildString {
        val seen = mutableSetOf<BoundQuery>()
        fun describe(query: BoundQuery, depth: Int) {
            val prefix = "  ".repeat(depth)
            if (!seen.add(query)) { append(prefix).append("Reuse bounded materialized relation\n"); return }
            when {
                query.query.values != null -> append(prefix).append("VALUES; rows=${query.query.values.size}\n")
                query.setOperands != null -> append(prefix).append("Local ${query.query.setOperation!!.operator}${if (query.query.setOperation.all) " ALL" else " DISTINCT"}\n")
                query.sources.isEmpty() -> append(prefix).append("Constant row\n")
                else -> query.sources.forEach { source ->
                    val table = source.table
                    if (table != null) {
                        val access = nativeAccess(query, source)
                        append(prefix).append(if (access.keyExpressions != null) "Get" else "Scan").append(" ${table.schema}.${table.name} AS ${source.qualifier.text}")
                        if (access.filters.isNotEmpty()) append("; exact scalar pushdown when values round-trip; store chooses indexed/key path")
                        append("; table scans ${if (sql.options.allowTableScan) "allowed" else "disabled"}; projected inputs=${query.inputs.filter { it.sourceId == source.id }.joinToString { it.column.name }}\n")
                    } else append(prefix).append("Bounded materialized relation AS ${source.qualifier.text}\n")
                }
            }
            query.joins.forEach { append(prefix).append("Local ${it.type} JOIN; bounded hash equijoin when fitting, otherwise nested loop; full ON residual\n") }
            if (query.query.where != null) append(prefix).append("SQL filter (three-valued logic)\n")
            if (query.aggregate) append(prefix).append("Local aggregate; group keys=${query.query.groupBy.size}\n")
            if (query.query.distinct) append(prefix).append("Local DISTINCT\n")
            if (query.query.orderBy.isNotEmpty()) append(prefix).append("Local sort; keys=${query.query.orderBy.size}\n")
            query.children.forEach { describe(it, depth + 1) }
        }
        describe(plan, 0)
        append("Offset/limit after SQL operators; shared bounded execution; Current reads are weakly consistent")
    }

    /** Values are checked before any store call. SQL NULL is valid even for required model fields. */
    fun execute(parameters: List<SqlValue> = emptyList(), options: SqlReadOptions = SqlReadOptions()): SqlExecution {
        if (parameters.size != this.parameters.size) throw SqlException(SqlErrorCode.PARAMETER, "Expected ${this.parameters.size} parameters, received ${parameters.size}")
        val converted = parameters.mapIndexed { index, value -> plan.bindingState.parameterDomains[index]?.let { domainValue(value, it, SqlErrorCode.PARAMETER) } ?: value }
        this.parameters.zip(converted).forEach { (metadata, value) ->
            if (value != SqlValue.Null && metadata.type != SqlType.ANY && value.type != metadata.type && !(metadata.type.isNumeric && value.type.isNumeric && metadata.type != SqlType.FLOAT64 && value.type != SqlType.FLOAT64)) {
                throw SqlException(SqlErrorCode.PARAMETER, "Parameter ${metadata.index + 1} expects ${metadata.type}, found ${value.type}")
            }
        }
        val boundParameters = converted.toList()
        return SqlExecution(columns, sql.options) { budget, emit ->
            if (plan.query.explain && !plan.query.analyze) {
                budget.returned()
                emit(SqlRow(columns, listOf(SqlValue.Text(explain()))))
            } else {
                SqlRuntime(sql, boundParameters, options, budget).execute(plan) { values ->
                    if (!plan.query.explain) emit(SqlRow(columns, values))
                }
                if (plan.query.explain) emit(SqlRow(columns, listOf(SqlValue.Text(explain() + "\nActual: requests=${budget.requests}, fetched=${budget.fetched}, rows=${budget.returned}"))))
            }
        }
    }
}

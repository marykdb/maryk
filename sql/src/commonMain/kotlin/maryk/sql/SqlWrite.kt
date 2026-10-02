package maryk.sql

import kotlinx.coroutines.withTimeoutOrNull
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.lib.exceptions.ParseException
import maryk.sql.syntax.SqlExpr
import maryk.sql.syntax.SqlMutation
import maryk.sql.syntax.SqlMutationKind
import maryk.sql.syntax.SqlName
import maryk.sql.syntax.SqlParseException
import maryk.sql.syntax.SqlParser
import maryk.sql.syntax.SqlQuery
import maryk.sql.syntax.SqlSelect
import maryk.sql.syntax.SqlTableSource

/** Outcome of one SQL mutation. Failed Maryk statuses are preserved as descriptive strings. */
data class SqlWriteResult(
    val affectedRows: Int,
    val failures: List<String> = emptyList(),
)

/** Bounded mutations share the query parser, evaluator, and one budget across reads and writes. */
internal class SqlWriter(private val sql: MarykSql) {
    private val budget = SqlBudget(sql.options)

    suspend fun execute(statement: String, parameters: List<SqlValue>): SqlWriteResult {
        val mutation = try { SqlParser(statement).parseMutation() }
        catch (error: SqlParseException) { throw error.toSqlException() }
        val table = table(mutation.table)
        return withTimeoutOrNull(sql.options.maxExecutionMillis) {
            budget.checkpoint()
            if (mutation.kind == SqlMutationKind.INSERT) insert(table, mutation, parameters)
            else mutate(table, mutation, parameters)
        } ?: budget.exceeded("Execution time")
    }

    private suspend fun insert(table: SqlTable, mutation: SqlMutation, parameters: List<SqlValue>): SqlWriteResult {
        val columns = columns(table, mutation.columns)
        if (mutation.rows.any { it.size != columns.size }) throw SqlException(SqlErrorCode.SYNTAX, "INSERT VALUES width does not match column list")
        if (mutation.rows.size.toLong() > sql.options.maxResultRows) budget.exceeded("Mutation row count")
        mutation.rows.flatten().forEach { expression ->
            if (containsColumn(expression) || containsAggregate(expression) || containsSubquery(expression)) {
                throw SqlException(SqlErrorCode.UNSUPPORTED, "INSERT VALUES requires scalar constant expressions", expression.position)
            }
        }
        // Keep cells independent: rows may use both typed domain parameters and textual names.
        val evaluated = evaluate(query(mutation.rows.flatten(), mutation.parameterCount), parameters).single()
        val objects = mutation.rows.indices.map { row ->
            budget.step()
            val pairs = columns.mapIndexedNotNull { index, column ->
                val value = evaluated[row * columns.size + index]
                if (value == SqlValue.Null) { nativeValue(column, value); null } else pair(column, value)
            }
            budget.retain(64L + columns.size * 32L)
            table.model.fromChanges(null, listOf(Change(*pairs.toTypedArray())))
        }
        budget.checkpoint()
        budget.request()
        val response = sql.dataStore.execute(table.model.add(*objects.toTypedArray()))
        return SqlWriteResult(response.statuses.count { it is AddSuccess<*> }, response.statuses.filterNot { it is AddSuccess<*> }.map { it.toString() })
    }

    private suspend fun mutate(table: SqlTable, mutation: SqlMutation, parameters: List<SqlValue>): SqlWriteResult {
        val assignments = columns(table, mutation.assignments.map { it.first })
        val where = mutation.where!!
        validateTarget(where)
        mutation.assignments.forEach { (_, expression) ->
            if (containsAggregate(expression) || containsSubquery(expression)) {
                throw SqlException(SqlErrorCode.UNSUPPORTED, "UPDATE assignments require scalar expressions", expression.position)
            }
        }
        val selection = listOf(systemColumn("__key"), systemColumn("__version")) + mutation.assignments.map { it.second }
        val request = query(selection, mutation.parameterCount).copy(
            from = SqlTableSource(listOf(SqlName(table.schema, true), SqlName(table.name, true)), null),
            where = where,
        )
        val rows = evaluate(request, parameters, SqlReadOptions(filterSoftDeleted = mutation.kind !in setOf(SqlMutationKind.HARD_DELETE, SqlMutationKind.UNDELETE)))
        if (rows.isEmpty()) return SqlWriteResult(0)
        if (rows.size != 1) throw SqlException(SqlErrorCode.EXECUTION_STATE, "A key mutation selected multiple rows")
        val selected = rows.single()
        val key = Key<IsRootDataModel>((selected[0] as SqlValue.Key).value.bytes)
        val version = (selected[1] as SqlValue.UInt64).value
        if (mutation.kind == SqlMutationKind.DELETE || mutation.kind == SqlMutationKind.HARD_DELETE) {
            budget.checkpoint()
            budget.request()
            val response = sql.dataStore.execute(table.model.delete(key, hardDelete = mutation.kind == SqlMutationKind.HARD_DELETE, lastVersion = version))
            return SqlWriteResult(response.statuses.count { it is DeleteSuccess<*> }, response.statuses.filterNot { it is DeleteSuccess<*> }.map { it.toString() })
        }
        val change = when (mutation.kind) {
            SqlMutationKind.UPDATE -> Change(*assignments.mapIndexed { index, column -> pair(column, selected[index + 2]) }.toTypedArray())
            SqlMutationKind.UNDELETE -> ObjectSoftDeleteChange(false)
            else -> error("Unexpected mutation")
        }
        budget.checkpoint()
        budget.request()
        val response = sql.dataStore.execute(table.model.change(key.change(change, lastVersion = version)))
        return SqlWriteResult(response.statuses.count { it is ChangeSuccess<*> }, response.statuses.filterNot { it is ChangeSuccess<*> }.map { it.toString() })
    }

    private fun validateTarget(where: SqlExpr) {
        val names = mutableSetOf<String>()
        fun visit(expression: SqlExpr) {
            if (expression is SqlExpr.Binary && expression.operator == "AND") {
                visit(expression.left)
                visit(expression.right)
                return
            }
            val comparison = expression as? SqlExpr.Binary
            val column = (comparison?.left as? SqlExpr.Column) ?: (comparison?.right as? SqlExpr.Column)
            val value = if (comparison?.left === column) comparison?.right else comparison?.left
            val name = column?.path?.singleOrNull()
            if (comparison?.operator != "=" || name == null || name.normalized !in setOf("__key", "__version") ||
                value == null || containsColumn(value) || containsAggregate(value) || containsSubquery(value) || !names.add(name.normalized)) {
                throw SqlException(SqlErrorCode.UNSUPPORTED, "Mutations require one __key equality and optionally one __version equality with constant expressions", expression.position)
            }
        }
        visit(where)
        if ("__key" !in names) throw SqlException(SqlErrorCode.UNSUPPORTED, "Mutations require WHERE __key = expression", where.position)
    }

    private suspend fun evaluate(query: SqlQuery, parameters: List<SqlValue>, readOptions: SqlReadOptions = SqlReadOptions()): List<List<SqlValue>> {
        val prepared = sql.prepare(query)
        val bound = prepared.bindParameters(parameters)
        val rows = mutableListOf<List<SqlValue>>()
        SqlRuntime(sql, bound, readOptions, budget).execute(prepared.plan) { values ->
            budget.retain(64L + values.sumOf(::valueBytes))
            rows += values
        }
        return rows
    }

    private fun table(parts: List<SqlName>): SqlTable {
        val schema = parts.takeIf { it.size == 2 }?.first() ?: SqlName(sql.options.defaultSchema)
        val table = sql.catalog.tables.singleOrNull { matches(schema, it.schema) && matches(parts.last(), it.name) }
            ?: throw SqlException(SqlErrorCode.BINDING, "Unknown table '${parts.joinToString(".") { it.text }}'")
        if (sql.dataStore.dataModelsById.values.none { it === table.model }) throw SqlException(SqlErrorCode.BINDING, "Table '${table.name}' is not registered in this data store")
        return table
    }

    private fun columns(table: SqlTable, names: List<SqlName>): List<CatalogColumn> {
        val columns = names.map { name ->
            table.bindings.singleOrNull { matches(name, it.column.name) }
                ?: throw SqlException(SqlErrorCode.BINDING, "Unknown column '${name.text}' in table '${table.name}'")
        }
        if (columns.distinct().size != columns.size) throw SqlException(SqlErrorCode.BINDING, "Mutation columns must be unique")
        if (columns.any { it.indices.size != 1 || it.column.type == SqlType.ANY }) throw SqlException(SqlErrorCode.UNSUPPORTED, "Mutations support direct scalar model properties")
        return columns
    }

    private fun nativeValue(column: CatalogColumn, value: SqlValue): Any? {
        if (value == SqlValue.Null) {
            if (!column.column.nullable) throw SqlException(SqlErrorCode.TYPE, "Column '${column.column.name}' is required")
            return null
        }
        if (column.column.type == SqlType.KEY) return Key<IsRootDataModel>((domainValue(value, column, SqlErrorCode.TYPE) as SqlValue.Key).value.bytes)
        if (column.column.type == SqlType.ENUM) return (domainValue(value, column, SqlErrorCode.TYPE) as SqlValue.Enum).let { enum ->
            (column.wrapper!!.definition as EnumDefinition<*>).enum.resolve(enum.name)
                ?: throw SqlException(SqlErrorCode.TYPE, "Unknown enum value '${enum.name}'")
        }
        val type = column.column.type
        if (value.type != type && !(type.isNumeric && value.type.isNumeric && type != SqlType.FLOAT64 && value.type != SqlType.FLOAT64)) {
            throw SqlException(SqlErrorCode.TYPE, "Column '${column.column.name}' expects $type, found ${value.type}; use CAST explicitly")
        }
        @Suppress("UNCHECKED_CAST")
        val definition = column.wrapper!!.definition as IsValueDefinition<Any, IsPropertyContext>
        val text = if (type == SqlType.INT64 || type == SqlType.UINT64) canonicalDecimal(value.decimal()) else value.display()
        val native = try { definition.fromString(text) }
        catch (error: IllegalArgumentException) { throw SqlException(SqlErrorCode.TYPE, "Invalid value for column '${column.column.name}'", cause = error) }
        catch (error: ParseException) { throw SqlException(SqlErrorCode.TYPE, "Invalid value for column '${column.column.name}'", cause = error) }
        if (sqlCompare(value, column.fromNative(native)) != 0) throw SqlException(SqlErrorCode.TYPE, "Value loses precision in column '${column.column.name}'; use CAST explicitly")
        return native
    }

    @Suppress("UNCHECKED_CAST")
    private fun pair(column: CatalogColumn, value: SqlValue) =
        (column.wrapper!!.ref() as IsPropertyReference<Any, IsValueDefinitionWrapper<Any, *, IsPropertyContext, *>, *>) with nativeValue(column, value)

    private fun systemColumn(name: String) = SqlExpr.Column(listOf(SqlName(name)), 0)

    private fun query(expressions: List<SqlExpr>, parameters: Int) = SqlQuery(
        select = expressions.map { SqlSelect(it, null) }, from = null, where = null, groupBy = emptyList(),
        having = null, orderBy = emptyList(), limit = null, offset = null, distinct = false, parameterCount = parameters,
    )
}

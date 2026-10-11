package maryk.sql

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.query.pairs.with
import maryk.lib.exceptions.ParseException
import maryk.sql.syntax.SqlName
import maryk.sql.syntax.SqlQuery

/** Bounded mutations share the query parser, evaluator, and one budget across reads and writes. */
internal class SqlWriter(private val sql: MarykSql) {
    internal val budget = SqlBudget(sql.options)

    internal suspend fun evaluate(query: SqlQuery, parameters: List<SqlValue>, readOptions: SqlReadOptions = SqlReadOptions()): List<List<SqlValue>> {
        val prepared = sql.prepare(query)
        val bound = prepared.bindParameters(parameters)
        val rows = mutableListOf<List<SqlValue>>()
        SqlRuntime(sql, bound, readOptions, budget).execute(prepared.plan) { values ->
            budget.retain(64L + values.sumOf(::valueBytes))
            rows += values
        }
        return rows
    }

    internal fun table(parts: List<SqlName>): SqlTable {
        val schema = parts.takeIf { it.size == 2 }?.first() ?: SqlName(sql.options.defaultSchema)
        val table = sql.catalog.tables.singleOrNull { matches(schema, it.schema) && matches(parts.last(), it.name) }
            ?: throw SqlException(SqlErrorCode.BINDING, "Unknown table '${parts.joinToString(".") { it.text }}'")
        if (sql.dataStore.dataModelsById.values.none { it === table.model }) throw SqlException(SqlErrorCode.BINDING, "Table '${table.name}' is not registered in this data store")
        return table
    }

    internal fun columns(table: SqlTable, names: List<SqlName>): List<CatalogColumn> {
        val columns = names.map { name ->
            table.bindings.singleOrNull { matches(name, it.column.name) }
                ?: throw SqlException(SqlErrorCode.BINDING, "Unknown column '${name.text}' in table '${table.name}'")
        }
        if (columns.distinct().size != columns.size) throw SqlException(SqlErrorCode.BINDING, "Mutation columns must be unique")
        if (columns.any { it.indices.size != 1 || it.column.type == SqlType.ANY }) throw SqlException(SqlErrorCode.UNSUPPORTED, "Mutations support direct scalar model properties")
        return columns
    }

    internal fun nativeValue(column: CatalogColumn, value: SqlValue): Any? {
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
    internal fun pair(column: CatalogColumn, value: SqlValue) =
        (column.wrapper!!.ref() as IsPropertyReference<Any, IsValueDefinitionWrapper<Any, *, IsPropertyContext, *>, *>) with nativeValue(column, value)

}

package maryk.sql

import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.emptyValues
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.values.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.sql.syntax.SqlTableSource
import maryk.sql.syntax.SqlJoin
import maryk.sql.syntax.SqlJoinType
import maryk.sql.syntax.SqlConflict
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.query.pairs.with
import maryk.lib.exceptions.ParseException
import maryk.sql.syntax.SqlMutation
import maryk.sql.syntax.SqlExpr
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
        if (columns.any { it.isSystem || it.column.type == SqlType.ANY }) throw SqlException(SqlErrorCode.UNSUPPORTED, "Mutations support scalar model properties, including embedded paths")
        return columns
    }

    internal fun conflictColumns(table: SqlTable, conflict: SqlConflict): List<CatalogColumn> {
            val bindings = conflict.columns.map { name -> table.bindings.singleOrNull { matches(name, it.column.name) } ?: throw SqlException(SqlErrorCode.BINDING, "Unknown conflict column '${name.text}'") }
            val keyParts = (table.model.Meta.keyDefinition as? Multiple)?.references ?: listOf(table.model.Meta.keyDefinition)
            val keyTarget = bindings.size == 1 && bindings.single().isKey || bindings.size == keyParts.size && bindings.zip(keyParts).all { (binding, part) ->
                (binding.reference as? IsIndexable)?.referenceStorageByteArray == part.referenceStorageByteArray
            }
            val uniqueTarget = bindings.size == 1 && (bindings.single().wrapper?.definition as? IsComparableDefinition<*, *>)?.unique == true
            if (!keyTarget && !uniqueTarget) throw SqlException(SqlErrorCode.UNSUPPORTED, "Conflict target must be the record key, its complete untransformed property key, or one unique property")
            if (bindings.distinct().size != bindings.size || bindings.any { it.column.type == SqlType.ANY }) throw SqlException(SqlErrorCode.BINDING, "Invalid conflict target")
            return bindings
    }

    internal fun mutationSource(table: SqlTable, mutation: SqlMutation, joined: Boolean = true): SqlTableSource {
        val other = mutation.joinedSource.takeIf { joined }
        return SqlTableSource(listOf(if (mutation.table.size == 2) mutation.table.first() else SqlName(table.schema, true), mutation.table.last()), mutation.alias,
            joins = if (other == null) emptyList() else listOf(SqlJoin(other.copy(joins = emptyList()), SqlJoinType.CROSS, null)) + other.joins)
    }

    internal fun validateExpressions(mutation: SqlMutation) {
        fun excludedVersion(expression: SqlExpr): Boolean = expression is SqlExpr.Column && expression.path.size == 2 &&
            expression.path.first().normalized == "excluded" && expression.path.last().normalized == "__version" || children(expression).any(::excludedVersion)
        if (mutation.conflict?.assignments?.any { excludedVersion(it.second) } == true) {
            throw SqlException(SqlErrorCode.UNSUPPORTED, "excluded.__version is unavailable because the incoming row has no committed version")
        }
        val assignments = mutation.assignments + mutation.conflict?.assignments.orEmpty()
        if (assignments.any { containsAggregate(it.second) || containsSubquery(it.second) || containsWindow(it.second) }) throw SqlException(SqlErrorCode.UNSUPPORTED, "Mutation assignments require scalar expressions")
        if (mutation.returning.any { containsAggregate(it.expression) || containsSubquery(it.expression) || containsWindow(it.expression) }) throw SqlException(SqlErrorCode.UNSUPPORTED, "RETURNING requires scalar expressions")
        if (mutation.rows.flatten().any { containsColumn(it) || containsAggregate(it) || containsSubquery(it) || containsWindow(it) }) throw SqlException(SqlErrorCode.UNSUPPORTED, "INSERT VALUES requires scalar constant expressions")
    }

    internal fun nativeValue(column: CatalogColumn, value: SqlValue): Any? {
        if (value == SqlValue.Null) {
            if (column.wrapper?.definition?.required == true) throw SqlException(SqlErrorCode.TYPE, "Column '${column.column.name}' is required")
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

    internal fun insertRecord(table: SqlTable, columns: List<CatalogColumn>, row: List<SqlValue>): Values<IsRootDataModel> {
        val supplied = columns.zip(row).mapNotNull { (column, value) -> nativeValue(column, value)?.let { column to it } }
        @Suppress("UNCHECKED_CAST")
        return nestedValues(table.model, 0, supplied) as Values<IsRootDataModel>
    }

    private fun nestedValues(model: IsValuesDataModel, depth: Int, values: List<Pair<CatalogColumn, Any>>): Values<IsValuesDataModel> {
        var result = model.emptyValues()
        for ((index, entries) in values.groupBy { it.first.indices[depth] }) {
            val scalar = entries.singleOrNull()?.takeIf { it.first.indices.size == depth + 1 }
            val value = if (scalar != null) scalar.second else {
                val embedded = model[index]!!.definition as? IsEmbeddedValuesDefinition<*, *>
                    ?: throw SqlException(SqlErrorCode.TYPE, "Expected an embedded scalar path")
                nestedValues(embedded.dataModel, depth + 1, entries)
            }
            result = result.copyWithValue(index, value)
        }
        return result
    }

    internal fun mutationPairs(table: SqlTable, columns: List<CatalogColumn>, row: List<SqlValue>, original: Values<*>?): List<IsReferenceValueOrNullPair<Any>> {
        val missing = linkedMapOf<List<UInt>, IsValuesDataModel>()
        for (column in columns) {
            var model: IsValuesDataModel = table.model
            var values: Any? = original
            for (depth in 0 until column.indices.lastIndex) {
                val definition = model[column.indices[depth]]!!.definition as IsEmbeddedValuesDefinition<*, *>
                values = (values as? Values<*>)?.original(column.indices[depth])
                if (values == null) { missing[column.indices.take(depth + 1)] = definition.dataModel; break }
                model = definition.dataModel
            }
        }
        fun under(column: CatalogColumn, prefix: List<UInt>) = column.indices.take(prefix.size) == prefix
        val result = columns.zip(row).filter { (column, _) -> missing.keys.none { under(column, it) } }.map { (column, value) -> pair(column, value) }.toMutableList()
        for ((prefix, model) in missing) {
            val entries = columns.zip(row).filter { under(it.first, prefix) }
            val native = entries.mapNotNull { (column, value) -> nativeValue(column, value)?.let { column to it } }
            // Assigning NULL to an absent optional leaf does not create an empty parent.
            if (native.isEmpty()) continue
            val value = nestedValues(model, prefix.size, native)
            val path = entries.first().first.column.name.split('.').take(prefix.size).joinToString(".")
            @Suppress("UNCHECKED_CAST")
            val reference = table.model.getPropertyReferenceByName(path) as IsPropertyReference<Any, IsValueDefinitionWrapper<Any, *, IsPropertyContext, *>, *>
            result += reference with value
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    internal fun pair(column: CatalogColumn, value: SqlValue) =
        (column.reference as IsPropertyReference<Any, IsValueDefinitionWrapper<Any, *, IsPropertyContext, *>, *>) with nativeValue(column, value)

}

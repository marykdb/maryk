package maryk.sql

import maryk.core.properties.definitions.IsComparableDefinition
import maryk.sql.syntax.LiteralKind
import maryk.sql.syntax.SqlCastType
import maryk.sql.syntax.SqlExpr
import maryk.sql.syntax.SqlName
import maryk.sql.syntax.SqlQuery
import maryk.sql.syntax.SqlSelect
import maryk.sql.syntax.SqlTableSource

/** Catalog metadata describes the locally exposed model surface, never server authorization. */
internal fun SqlCatalog.metadata(source: SqlTableSource): SqlQuery? {
    if (source.name.size != 2 || !matches(source.name[0], "information_schema")) return null
    val names: List<String>
    val rows: List<List<SqlValue>>
    when (source.name[1].normalized) {
        "tables" -> {
            names = listOf("table_schema", "table_name", "model_name", "table_type")
            rows = tables.map { listOf(SqlValue.Text(it.schema), SqlValue.Text(it.name), SqlValue.Text(it.model.Meta.name), SqlValue.Text("MODEL")) }
        }
        "columns" -> {
            names = listOf("table_schema", "table_name", "column_name", "ordinal_position", "data_type", "is_nullable", "is_system", "is_writable", "is_unique")
            rows = tables.flatMap { table -> table.bindings.mapIndexed { index, column -> listOf(
                SqlValue.Text(table.schema), SqlValue.Text(table.name), SqlValue.Text(column.column.name), SqlValue.Int64(index.toLong() + 1),
                SqlValue.Text(column.column.type.name), SqlValue.Bool(column.column.nullable), SqlValue.Bool(column.isSystem),
                SqlValue.Bool(!column.isSystem && column.column.type != SqlType.ANY), SqlValue.Bool(column.isKey || (column.wrapper?.definition as? IsComparableDefinition<*, *>)?.unique == true),
            ) } }
        }
        "indexes" -> {
            names = listOf("table_schema", "table_name", "index_ordinal", "is_primary", "encoding")
            rows = tables.flatMap { table -> (listOf(table.model.Meta.keyDefinition) + table.model.Meta.indexes.orEmpty()).mapIndexed { index, definition -> listOf(
                SqlValue.Text(table.schema), SqlValue.Text(table.name), SqlValue.Int64(index.toLong()), SqlValue.Bool(index == 0), SqlValue.Text(definition.referenceStorageByteArray.toString()),
            ) } }
        }
        else -> throw SqlException(SqlErrorCode.BINDING, "Unknown information_schema table '${source.name[1].text}'")
    }
    fun literal(value: SqlValue) = when (value) {
        is SqlValue.Text -> SqlExpr.Literal(value.value, LiteralKind.STRING, 0)
        is SqlValue.Bool -> SqlExpr.Literal(value.value.toString(), LiteralKind.BOOLEAN, 0)
        else -> SqlExpr.Literal(value.display(), LiteralKind.NUMBER, 0)
    }
    val values = SqlQuery(emptyList(), null, null, emptyList(), null, emptyList(), null, null, false, 0,
        values = rows.ifEmpty { listOf(names.map { SqlValue.Null }) }.map { row -> row.map { if (it == SqlValue.Null) SqlExpr.Literal("NULL", LiteralKind.NULL, 0) else literal(it) } })
    return SqlQuery(names.mapIndexed { index, name ->
        val expression = SqlExpr.Column(listOf(SqlName("column${index + 1}")), 0)
        val type = when (name) { "ordinal_position", "index_ordinal" -> "INT64"; "is_nullable", "is_system", "is_writable", "is_unique", "is_primary" -> "BOOLEAN"; else -> "TEXT" }
        SqlSelect(if (rows.isEmpty()) SqlExpr.Cast(expression, SqlCastType(type), 0) else expression, SqlName(name))
    },
        SqlTableSource(emptyList(), SqlName("metadata"), derived = values),
        if (rows.isEmpty()) SqlExpr.Literal("false", LiteralKind.BOOLEAN, 0) else null,
        emptyList(), null, emptyList(), null, null, false, 0)
}

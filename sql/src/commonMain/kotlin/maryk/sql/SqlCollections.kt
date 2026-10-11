package maryk.sql

import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.values.Values
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.properties.definitions.IsSetDefinition

private fun elementColumn(name: String, definition: Any?, depth: Int): CatalogColumn {
    val domain = when (definition) {
        is EnumDefinition<*> -> definition.enum.name
        is IsReferenceDefinition<*, *> -> definition.dataModel.Meta.name
        else -> null
    }
    return CatalogColumn(SqlColumn(name, if (collectionDefinition(definition)) SqlType.COLLECTION else scalarSqlType(definition)), emptyList(), null, domain,
        keyModel = (definition as? IsReferenceDefinition<*, *>)?.dataModel,
        collectionElementColumns = if (collectionDefinition(definition)) collectionColumns(definition, depth + 1) else null)
}

private fun collectionDefinition(definition: Any?) = definition is IsListDefinition<*, *> || definition is IsSetDefinition<*, *> || definition is IsMapDefinition<*, *, *>

private fun elementDefinition(definition: Any?): Any? = when (definition) {
    is IsListDefinition<*, *> -> definition.valueDefinition
    is IsSetDefinition<*, *> -> definition.valueDefinition
    is IsMapDefinition<*, *, *> -> definition.valueDefinition
    else -> null
}

internal fun collectionColumns(definition: Any?, depth: Int = 0): List<CatalogColumn>? {
    if (depth >= 32) throw SqlLimitException("collectionDepth", 32uL, (depth + 1).toULong())
    val value = elementDefinition(definition) ?: return null
    // Maryk storage does not encode complex list/set elements. Maps own nested qualifiers.
    if (definition !is IsMapDefinition<*, *, *> && (value is IsEmbeddedValuesDefinition<*, *> || collectionDefinition(value))) return null
    val values = if (value is IsEmbeddedValuesDefinition<*, *>) {
        mutableListOf<CatalogColumn>().also { collectColumns(value.dataModel, emptyList(), "value.", false, it, 0) }
    } else listOf(elementColumn("value", value, depth))
    val key = if (definition is IsMapDefinition<*, *, *>) elementColumn("entry_key", definition.keyDefinition, depth)
        else CatalogColumn(SqlColumn("entry_key", SqlType.NULL), emptyList(), null)
    if (key.column.type == SqlType.ANY || values.any { it.column.type == SqlType.ANY && !collectionDefinition(it.wrapper?.definition) }) return null
    return values.map { column -> if (collectionDefinition(column.wrapper?.definition)) column.copy(column = column.column.copy(type = SqlType.COLLECTION)) else column } + key
}

internal fun collectionValue(definition: Any?, value: Any): SqlValue.Collection? {
    val columns = collectionColumns(definition) ?: return null
    val element = elementDefinition(definition)
    fun convert(item: Any?): SqlCollectionEntry {
        if (item == null) return SqlCollectionEntry(value = SqlValue.Null)
        if (element is IsEmbeddedValuesDefinition<*, *>) {
            val fields = columns.filter { it.column.name != "entry_key" }.associate { column ->
                var native: Any? = item
                for (index in column.indices) native = (native as? Values<*>)?.original(index)
                column.column.name to column.fromNative(native)
            }
            return SqlCollectionEntry(value = SqlValue.Null, fields = fields)
        }
        return SqlCollectionEntry(value = if (collectionDefinition(element)) collectionValue(element, item)
            ?: throw SqlException(SqlErrorCode.UNSUPPORTED, "Unsupported nested collection") else columns.first().fromNative(item))
    }
    val entries = when (value) {
        is Map<*, *> -> value.map { (key, item) -> convert(item).copy(key = columns.last().fromNative(key)) }
        is Collection<*> -> value.map(::convert)
        else -> return null
    }
    return SqlValue.Collection(when (definition) { is IsListDefinition<*, *> -> SqlCollectionKind.LIST; is IsSetDefinition<*, *> -> SqlCollectionKind.SET; else -> SqlCollectionKind.MAP }, entries)
}

internal fun collectionRows(source: BoundSource, value: SqlValue): Iterable<InputRow> {
    if (value == SqlValue.Null) return emptyList()
    val collection = value as? SqlValue.Collection ?: throw SqlException(SqlErrorCode.TYPE, "UNNEST requires a collection")
    return collection.entries.asSequence().mapIndexed { index, entry ->
        source.columns.associateWith { column -> when (column.column.name) {
            "value" -> entry.value
            "entry_key" -> entry.key
            "ordinality" -> SqlValue.Int64(index.toLong() + 1)
            else -> entry.fields[column.column.name] ?: SqlValue.Null
        } }
    }.asIterable()
}

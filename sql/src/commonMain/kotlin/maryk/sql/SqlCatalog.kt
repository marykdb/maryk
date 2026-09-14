package maryk.sql

import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.DecimalDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.numeric.NumberType
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.sql.syntax.SqlName

/** A model exposed as a SQL table; an explicit column set can restrict the local query surface. */
class SqlTable(val name: String, val model: IsRootDataModel, val schema: String = "public", exposedColumns: Set<String>? = null) {
    internal val bindings: List<CatalogColumn>
    val columns: List<SqlColumn>

    init {
        require(name.isNotEmpty() && schema.isNotEmpty()) { "Table and schema names cannot be empty" }
        val all = mutableListOf(
            CatalogColumn(SqlColumn("__key", SqlType.KEY, false), emptyList(), null, model.Meta.name, keyModel = model, isKey = true),
            CatalogColumn(SqlColumn("__version", SqlType.UINT64, false), emptyList(), null, isVersion = true),
        )
        collectColumns(model, emptyList(), "", false, all, 0)
        if (all.groupBy { asciiLower(it.column.name) }.any { it.value.size > 1 }) {
            throw SqlException(SqlErrorCode.BINDING, "Ambiguous columns in table '$name'")
        }
        exposedColumns?.forEach { requested ->
            if (all.none { it.column.name == requested }) throw SqlException(SqlErrorCode.BINDING, "Unknown exposed column '$requested'")
        }
        bindings = all.filter { it.isSystem || exposedColumns == null || it.column.name in exposedColumns }
        columns = bindings.map { it.column }
    }
}

/** An immutable name mapping. This does not replace server-side authorization. */
class SqlCatalog(tables: List<SqlTable>) {
    val tables = tables.toList()
    init {
        if (tables.groupBy { asciiLower(it.schema) to asciiLower(it.name) }.any { it.value.size > 1 }) {
            throw SqlException(SqlErrorCode.BINDING, "Ambiguous SQL table names")
        }
    }
    companion object {
        fun from(dataStore: IsDataStore): SqlCatalog = SqlCatalog(dataStore.dataModelsById.values.map { SqlTable(it.Meta.name, it) })
    }
}

internal data class CatalogColumn(
    val column: SqlColumn,
    val indices: List<UInt>,
    val wrapper: IsDefinitionWrapper<*, *, *, *>?,
    val domain: String? = null,
    val sourceId: Int = -1,
    val outputIndex: Int? = null,
    val keyModel: IsRootDataModel? = null,
    val identifier: SqlName? = null,
    val parameterOrigins: Set<Int> = emptySet(),
    val isKey: Boolean = false,
    val isVersion: Boolean = false,
) {
    val isSystem: Boolean get() = isKey || isVersion

    fun read(record: ValuesWithMetaData<*>): SqlValue {
        if (isKey) return SqlValue.Key(domain!!, record.key)
        if (isVersion) return SqlValue.UInt64(record.lastVersion)
        var value: Any? = record.values
        for (index in indices) value = (value as? Values<*>)?.original(index)
        if (value == null) return SqlValue.Null
        return when (column.type) {
            SqlType.INT64 -> SqlValue.Int64(when (value) {
                is Long -> value
                is Int -> value.toLong()
                is Short -> value.toLong()
                is Byte -> value.toLong()
                is ULong -> value.toLong()
                is UInt -> value.toLong()
                is UShort -> value.toLong()
                is UByte -> value.toLong()
                else -> return SqlValue.of(value)
            })
            SqlType.UINT64 -> SqlValue.UInt64(when (value) {
                is ULong -> value
                is UInt -> value.toULong()
                is UShort -> value.toULong()
                is UByte -> value.toULong()
                is Long -> value.toULong()
                is Int -> value.toULong()
                is Short -> value.toULong()
                is Byte -> value.toULong()
                else -> return SqlValue.of(value)
            })
            SqlType.FLOAT64 -> (value as? Number)?.let { SqlValue.Float64(it.toDouble()) } ?: SqlValue.of(value)
            SqlType.KEY -> SqlValue.Key(domain!!, value as Bytes)
            SqlType.ENUM -> (value as IndexedEnum).let { SqlValue.Enum(domain!!, it.index, it.name) }
            SqlType.ANY -> throw SqlException(SqlErrorCode.UNSUPPORTED, "Column '${column.name}' is not scalar; select supported columns explicitly")
            else -> SqlValue.of(value)
        }
    }
}

private fun collectColumns(
    model: IsValuesDataModel,
    parents: List<UInt>,
    prefix: String,
    parentNullable: Boolean,
    result: MutableList<CatalogColumn>,
    depth: Int,
) {
    if (depth > 32) throw SqlException(SqlErrorCode.LIMIT, "Embedded model nesting exceeds 32 levels")
    for (wrapper in model) {
        val definition = wrapper.definition
        val name = prefix + wrapper.name
        val indices = parents + wrapper.index
        val nullable = parentNullable || !definition.required
        if (definition is IsEmbeddedValuesDefinition<*, *>) {
            collectColumns(definition.dataModel, indices, "$name.", nullable, result, depth + 1)
            continue
        }
        val type = when (definition) {
            is BooleanDefinition -> SqlType.BOOLEAN
            is StringDefinition -> SqlType.TEXT
            is DecimalDefinition -> SqlType.DECIMAL
            is NumberDefinition<*> -> when (definition.type.type) {
                NumberType.UInt8Type, NumberType.UInt16Type, NumberType.UInt32Type, NumberType.UInt64Type -> SqlType.UINT64
                NumberType.Float32Type, NumberType.Float64Type -> SqlType.FLOAT64
                else -> SqlType.INT64
            }
            is DateDefinition -> SqlType.DATE
            is TimeDefinition -> SqlType.TIME
            is DateTimeDefinition -> SqlType.TIMESTAMP
            is IsReferenceDefinition<*, *> -> SqlType.KEY
            is EnumDefinition<*> -> SqlType.ENUM
            is FixedBytesDefinition, is FlexBytesDefinition -> SqlType.BINARY
            else -> SqlType.ANY
        }
        val domain = when (definition) {
            is IsReferenceDefinition<*, *> -> definition.dataModel.Meta.name
            is EnumDefinition<*> -> definition.enum.name
            else -> null
        }
        result += CatalogColumn(SqlColumn(name, type, nullable), indices, wrapper, domain, keyModel = (definition as? IsReferenceDefinition<*, *>)?.dataModel)
    }
}

package maryk.sql

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Decimal

/** Portable result/parameter types. Exact integers never pass through Double. */
enum class SqlType { NULL, BOOLEAN, INT64, UINT64, DECIMAL, FLOAT64, TEXT, BINARY, DATE, TIME, TIMESTAMP, KEY, ENUM, ANY, COLLECTION }

sealed interface SqlValue {
    val type: SqlType
    data object Null : SqlValue { override val type = SqlType.NULL }
    data class Bool(val value: Boolean) : SqlValue { override val type = SqlType.BOOLEAN }
    data class Int64(val value: Long) : SqlValue { override val type = SqlType.INT64 }
    data class UInt64(val value: ULong) : SqlValue { override val type = SqlType.UINT64 }
    data class Exact(val value: Decimal) : SqlValue { override val type = SqlType.DECIMAL }
    data class Float64(val value: Double) : SqlValue {
        override val type = SqlType.FLOAT64
        init { require(value.isFinite()) { "SQL floating values must be finite" } }
    }
    data class Text(val value: String) : SqlValue { override val type = SqlType.TEXT }
    data class Binary(val value: Bytes) : SqlValue { override val type = SqlType.BINARY }
    data class Date(val value: LocalDate) : SqlValue { override val type = SqlType.DATE }
    data class Time(val value: LocalTime) : SqlValue { override val type = SqlType.TIME }
    data class Timestamp(val value: LocalDateTime) : SqlValue { override val type = SqlType.TIMESTAMP }
    data class Key(val model: String, val value: Bytes) : SqlValue { override val type = SqlType.KEY }
    data class Enum(val domain: String, val index: UInt, val name: String) : SqlValue { override val type = SqlType.ENUM }

    class Collection(val kind: SqlCollectionKind, entries: List<SqlCollectionEntry>) : SqlValue {
        override val type = SqlType.COLLECTION
        val entries = entries.map { it.copy(fields = it.fields.toMap()) }
        init {
            require(this.entries.all { it.key !is Collection }) { "Collection keys must be scalar" }
            require(this.entries.all { if (kind == SqlCollectionKind.MAP) it.key != Null else it.key == Null }) { "Only map entries carry non-NULL keys" }
        }
        override fun toString() = "${kind.name}[${entries.joinToString { it.toString() }}]"
    }

    companion object {
        // Static overloads preserve the caller's numeric type even on JavaScript.
        fun of(value: Byte): SqlValue = Int64(value.toLong())
        fun of(value: Short): SqlValue = Int64(value.toLong())
        fun of(value: Int): SqlValue = Int64(value.toLong())
        fun of(value: Long): SqlValue = Int64(value)
        fun of(value: Float): SqlValue = Float64(value.toDouble())
        fun of(value: Double): SqlValue = Float64(value)

        /** Convert scalar Kotlin values; use typed Key/Enum values for model identities. */
        fun of(value: Any?): SqlValue = when (value) {
            null -> Null
            is SqlValue -> value
            is Boolean -> Bool(value)
            is Long -> Int64(value)
            // JS erases Byte/Short/Int/Float/Double to Number. Its runtime class distinguishes
            // Int-range integral values from doubles; `is Byte` would also match fractions.
            is Number -> when (value::class) {
                Byte::class, Short::class, Int::class -> Int64(value.toLong())
                Float::class, Double::class -> Float64(value.toDouble())
                else -> throw SqlException(SqlErrorCode.TYPE, "Unsupported numeric type ${value::class.simpleName}")
            }
            is UByte -> UInt64(value.toULong())
            is UShort -> UInt64(value.toULong())
            is UInt -> UInt64(value.toULong())
            is ULong -> UInt64(value)
            is Decimal -> Exact(value)
            is String -> Text(value)
            is ByteArray -> Binary(Bytes(value))
            is Bytes -> Binary(value)
            is LocalDate -> Date(value)
            is LocalTime -> Time(value)
            is LocalDateTime -> Timestamp(value)
            else -> throw SqlException(SqlErrorCode.TYPE, "Unsupported scalar type ${value::class.simpleName}")
        }
    }
}

/** Stable categories; source positions are zero-based UTF-16 offsets when available. */
enum class SqlErrorCode { SYNTAX, BINDING, TYPE, UNSUPPORTED, PARAMETER, ARITHMETIC, LIMIT, EXECUTION_STATE, CONSISTENCY }

open class SqlException(
    val code: SqlErrorCode,
    message: String,
    val position: Int? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** A budget failure that callers can inspect without parsing its message. */
class SqlLimitException(
    val limit: String,
    val maximum: ULong,
    val actual: ULong? = null,
) : SqlException(SqlErrorCode.LIMIT, "$limit exceeds SQL budget: ${actual ?: "unknown"} > $maximum")

data class SqlColumn(val name: String, val type: SqlType, val nullable: Boolean = true)
data class SqlParameter(val index: Int, val type: SqlType, val nullable: Boolean = true)

/** Ordered cells; duplicate output labels must be addressed by index. */
class SqlRow internal constructor(val columns: List<SqlColumn>, values: List<SqlValue>) {
    val values: List<SqlValue> = values.toList()
    operator fun get(index: Int): SqlValue = values[index]
    operator fun get(name: String): SqlValue {
        val matches = columns.indices.filter { columns[it].name == name }
        if (matches.size != 1) throw SqlException(SqlErrorCode.BINDING, "Expected one output column named '$name', found ${matches.size}")
        return values[matches.single()]
    }
    override fun toString(): String = values.toString()
}

enum class SqlCollectionKind { LIST, SET, MAP }
data class SqlCollectionEntry(val key: SqlValue = SqlValue.Null, val value: SqlValue, val fields: Map<String, SqlValue> = emptyMap())

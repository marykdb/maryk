package maryk.sql

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Decimal

/** Portable result/parameter types. Exact integers never pass through Double. */
enum class SqlType { NULL, BOOLEAN, INT64, UINT64, DECIMAL, FLOAT64, TEXT, BINARY, DATE, TIME, TIMESTAMP, KEY, ENUM, ANY }

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

    companion object {
        /** Convert scalar Kotlin values; use typed Key/Enum values for model identities. */
        fun of(value: Any?): SqlValue = when (value) {
            null -> Null
            is SqlValue -> value
            is Boolean -> Bool(value)
            is Byte -> Int64(value.toLong())
            is Short -> Int64(value.toLong())
            is Int -> Int64(value.toLong())
            is Long -> Int64(value)
            is UByte -> UInt64(value.toULong())
            is UShort -> UInt64(value.toULong())
            is UInt -> UInt64(value.toULong())
            is ULong -> UInt64(value)
            is Decimal -> Exact(value)
            is Float -> Float64(value.toDouble())
            is Double -> Float64(value)
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

class SqlException(
    val code: SqlErrorCode,
    message: String,
    val position: Int? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

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

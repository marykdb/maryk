package maryk.sql

import maryk.core.properties.types.Decimal

internal val SqlType.isNumeric: Boolean
    get() = this == SqlType.INT64 || this == SqlType.UINT64 || this == SqlType.DECIMAL || this == SqlType.FLOAT64

internal fun SqlValue.decimal(): Decimal = when (this) {
    is SqlValue.Int64 -> Decimal.parse(value.toString())
    is SqlValue.UInt64 -> Decimal.parse(value.toString())
    is SqlValue.Exact -> value
    else -> throw SqlException(SqlErrorCode.TYPE, "Expected an exact number, found $type")
}

/** Exact numeric unification never changes a value through floating point. */
internal fun coerceSqlValue(value: SqlValue, type: SqlType): SqlValue = when {
    value == SqlValue.Null || type == SqlType.ANY || type == value.type -> value
    type == SqlType.DECIMAL && value.type.isNumeric && value !is SqlValue.Float64 -> SqlValue.Exact(value.decimal())
    else -> throw SqlException(SqlErrorCode.TYPE, "Value ${value.type} does not match bound output type $type")
}

internal fun SqlValue.truth(): Boolean? = when (this) {
    SqlValue.Null -> null
    is SqlValue.Bool -> value
    else -> throw SqlException(SqlErrorCode.TYPE, "Expected BOOLEAN, found $type")
}

internal fun truthValue(value: Boolean?): SqlValue = value?.let(SqlValue::Bool) ?: SqlValue.Null

internal fun sqlAnd(left: SqlValue, right: SqlValue): SqlValue {
    val l = left.truth()
    val r = right.truth()
    return truthValue(when { l == false || r == false -> false; l == null || r == null -> null; else -> true })
}

internal fun sqlOr(left: SqlValue, right: SqlValue): SqlValue {
    val l = left.truth()
    val r = right.truth()
    return truthValue(when { l == true || r == true -> true; l == null || r == null -> null; else -> false })
}

internal fun sqlNot(value: SqlValue): SqlValue = truthValue(value.truth()?.not())

internal fun sqlCompare(left: SqlValue, right: SqlValue): Int? {
    if (left == SqlValue.Null || right == SqlValue.Null) return null
    if (left.type.isNumeric && right.type.isNumeric) {
        if (left is SqlValue.Float64 || right is SqlValue.Float64) {
            if (left !is SqlValue.Float64 || right !is SqlValue.Float64) {
                throw SqlException(SqlErrorCode.TYPE, "Mixing floating and exact numbers requires an explicit CAST")
            }
            // SQL treats the two IEEE representations of zero as equal.
            return if (left.value == right.value) 0 else left.value.compareTo(right.value)
        }
        return left.decimal().compareTo(right.decimal())
    }
    return when {
        left is SqlValue.Bool && right is SqlValue.Bool -> left.value.compareTo(right.value)
        left is SqlValue.Text && right is SqlValue.Text -> left.value.compareTo(right.value)
        left is SqlValue.Binary && right is SqlValue.Binary -> left.value.compareTo(right.value)
        left is SqlValue.Date && right is SqlValue.Date -> left.value.compareTo(right.value)
        left is SqlValue.Time && right is SqlValue.Time -> left.value.compareTo(right.value)
        left is SqlValue.Timestamp && right is SqlValue.Timestamp -> left.value.compareTo(right.value)
        left is SqlValue.Key && right is SqlValue.Key && left.model == right.model -> left.value.compareTo(right.value)
        left is SqlValue.Enum && right is SqlValue.Enum && left.domain == right.domain -> left.index.compareTo(right.index)
        else -> throw SqlException(SqlErrorCode.TYPE, "Cannot compare ${left.type} and ${right.type} (or different model/enum domains)")
    }
}

/** Hashing for grouping, DISTINCT and joins must agree with SQL numeric equality. */
internal fun sqlKey(value: SqlValue): Any = when (value) {
    is SqlValue.Int64, is SqlValue.UInt64, is SqlValue.Exact -> SqlNumericKey(canonicalDecimal(value.decimal()))
    is SqlValue.Float64 -> if (value.value == 0.0) SqlValue.Float64(0.0) else value
    is SqlValue.Enum -> value.domain to value.index
    else -> value
}

private data class SqlNumericKey(val text: String)

internal fun canonicalDecimal(value: Decimal): String {
    val text = value.toString()
    return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
}

/** Explicit floating-to-exact CAST expands decimal exponent notation without another binary conversion. */
internal fun expandScientific(text: String): String {
    val exponentAt = text.indexOfFirst { it == 'e' || it == 'E' }
    if (exponentAt < 0) return text
    val mantissa = text.substring(0, exponentAt)
    val exponent = text.substring(exponentAt + 1).toInt()
    val negative = mantissa.startsWith('-')
    val absolute = mantissa.removePrefix("-")
    val digits = absolute.replace(".", "")
    val point = (absolute.indexOf('.').takeIf { it >= 0 } ?: absolute.length) + exponent
    val expanded = when {
        point <= 0 -> "0." + "0".repeat(-point) + digits
        point >= digits.length -> digits + "0".repeat(point - digits.length)
        else -> digits.substring(0, point) + "." + digits.substring(point)
    }
    val canonical = if ('.' in expanded) expanded.trimEnd('0').trimEnd('.') else expanded
    return if (negative) "-$canonical" else canonical
}

internal fun parseSqlNumber(text: String): SqlValue = try {
    if (text.count { it in '0'..'9' } > 1024) throw SqlException(SqlErrorCode.LIMIT, "Numeric literal exceeds 1024 digits")
    when {
        'e' in text || 'E' in text -> SqlValue.Float64(text.toDouble())
        '.' in text -> SqlValue.Exact(Decimal.parse(text.let { if (it.startsWith('.')) "0$it" else if (it.endsWith('.')) "${it}0" else it }))
        else -> text.toLongOrNull()?.let(SqlValue::Int64)
            ?: text.toULongOrNull()?.let(SqlValue::UInt64)
            ?: SqlValue.Exact(Decimal.parse(text))
    }
} catch (error: IllegalArgumentException) {
    if (error is SqlException) throw error
    throw SqlException(SqlErrorCode.ARITHMETIC, "Invalid or out-of-range numeric literal", cause = error)
}

internal fun SqlValue.display(): String = when (this) {
    SqlValue.Null -> "NULL"
    is SqlValue.Bool -> value.toString()
    is SqlValue.Int64 -> value.toString()
    is SqlValue.UInt64 -> value.toString()
    is SqlValue.Exact -> value.toString()
    is SqlValue.Float64 -> value.toString()
    is SqlValue.Text -> value
    is SqlValue.Binary -> value.toString()
    is SqlValue.Date -> value.toString()
    is SqlValue.Time -> value.toString()
    is SqlValue.Timestamp -> value.toString()
    is SqlValue.Key -> value.toString()
    is SqlValue.Enum -> name
}

internal fun asciiLower(text: String): String = buildString(text.length) {
    text.forEach { append(if (it in 'A'..'Z') it + ('a' - 'A') else it) }
}

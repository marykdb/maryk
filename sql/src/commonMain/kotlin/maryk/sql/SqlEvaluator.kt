package maryk.sql

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Decimal
import maryk.sql.syntax.SqlExpr
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

internal typealias InputRow = Map<CatalogColumn, SqlValue>

internal class SqlBudget(val options: SqlOptions) {
    var steps = 0L
    var fetched = 0L
    var returned = 0L
    var requests = 0
    var retainedBytes = 0L
    var peakBytes = 0L
    var snapshotVersion: ULong? = null
    suspend fun step() {
        if (++steps > options.maxExpressionSteps) exceeded("Expression work")
        if (steps % 1024L == 0L) yield()
    }
    suspend fun checkpoint() = coroutineContext.ensureActive()
    fun request() { if (++requests > options.maxRequests) exceeded("Store request count") }
    fun fetched(count: Int) { fetched += count; if (fetched > options.maxFetchedRows) exceeded("Fetched row count") }
    fun returned() { if (++returned > options.maxResultRows) exceeded("Result row count") }
    fun retain(bytes: Long) {
        if (bytes > options.maxBufferedBytes - retainedBytes) exceeded("Logical buffer size")
        retainedBytes += bytes
        peakBytes = maxOf(peakBytes, retainedBytes)
    }
    fun release(bytes: Long) { retainedBytes = maxOf(0, retainedBytes - bytes) }
    fun checkValue(value: SqlValue): SqlValue {
        if (value is SqlValue.Exact && value.value.toString().count { it in '0'..'9' } > 1024) exceeded("Numeric precision")
        if (valueBytes(value) > options.maxBufferedBytes) exceeded("Scalar value size")
        return value
    }
    fun exceeded(subject: String): Nothing = throw SqlException(SqlErrorCode.LIMIT, "$subject exceeds configured SQL budget")
}

internal fun valueBytes(value: SqlValue): Long = 32L + when (value) {
    is SqlValue.Text -> value.value.length.toLong() * 2
    is SqlValue.Binary -> value.value.size.toLong()
    is SqlValue.Key -> value.value.size.toLong() + value.model.length * 2L
    is SqlValue.Enum -> value.domain.length * 2L + value.name.length * 2L
    is SqlValue.Exact -> value.value.toString().length * 2L
    else -> 16L
}

internal class SqlEvaluator(
    private val plan: BoundQuery,
    private val parameters: List<SqlValue>,
    val budget: SqlBudget,
    private val subquery: suspend (BoundQuery) -> List<List<SqlValue>> = { throw SqlException(SqlErrorCode.UNSUPPORTED, "Subqueries require a query runtime") },
) {
    suspend fun eval(expression: SqlExpr, row: InputRow = emptyMap(), group: List<InputRow>? = null): SqlValue {
        budget.step()
        val result = try {
            when (expression) {
                is SqlExpr.Literal -> plan.literals[expression] ?: literalValue(expression)
                is SqlExpr.Parameter -> parameters[expression.index]
                is SqlExpr.Column -> row[plan.references.getValue(expression)] ?: SqlValue.Null
                is SqlExpr.Star -> throw SqlException(SqlErrorCode.BINDING, "Unexpected wildcard", expression.position)
                is SqlExpr.Unary -> {
                    val value = eval(expression.operand, row, group)
                    when {
                        expression.operator == "NOT" -> sqlNot(value)
                        value == SqlValue.Null -> value
                        value is SqlValue.Float64 -> SqlValue.Float64(if (expression.operator == "-") -value.value else value.value)
                        else -> SqlValue.Exact(if (expression.operator == "-") -value.decimal() else value.decimal())
                    }
                }
                is SqlExpr.Binary -> binary(expression, row, group)
                is SqlExpr.Is -> {
                    val value = eval(expression.operand, row, group)
                    val matches = when (expression.test) {
                        "NULL", "UNKNOWN" -> value == SqlValue.Null
                        "TRUE" -> value.truth() == true
                        "FALSE" -> value.truth() == false
                        else -> throw SqlException(SqlErrorCode.BINDING, "Unknown IS predicate")
                    }
                    SqlValue.Bool(matches != expression.negated)
                }
                is SqlExpr.In -> {
                    val value = eval(expression.operand, row, group)
                    var result: Boolean? = false
                    for (candidate in expression.values) {
                        val comparison = sqlCompare(value, eval(candidate, row, group))
                        if (comparison == 0) { result = true; break }
                        if (comparison == null) result = null
                    }
                    truthValue(result?.let { it != expression.negated })
                }
                is SqlExpr.Between -> {
                    val value = eval(expression.operand, row, group)
                    val lower = sqlCompare(value, eval(expression.lower, row, group))?.let { it >= 0 }
                    val upper = sqlCompare(value, eval(expression.upper, row, group))?.let { it <= 0 }
                    sqlAnd(truthValue(lower), truthValue(upper)).let { if (expression.negated) sqlNot(it) else it }
                }
                is SqlExpr.Like -> {
                    val value = eval(expression.operand, row, group)
                    val pattern = eval(expression.pattern, row, group)
                    val escape = expression.escape?.let { eval(it, row, group) }
                    if (value == SqlValue.Null || pattern == SqlValue.Null || escape == SqlValue.Null) SqlValue.Null else {
                        val matched = like(value.text(), pattern.text(), escape?.text(), budget)
                        SqlValue.Bool(matched != expression.negated)
                    }
                }
                is SqlExpr.Call -> if (expression.name.normalized in aggregateNames) aggregate(expression, group ?: error("Missing aggregate group")) else function(expression, row, group)
                is SqlExpr.Cast -> cast(eval(expression.operand, row, group), expression)
                is SqlExpr.Case -> {
                    val operand = expression.operand?.let { eval(it, row, group) }
                    var selected: SqlExpr? = expression.otherwise
                    for ((condition, value) in expression.branches) {
                        val conditionValue = eval(condition, row, group)
                        if (if (expression.operand == null) conditionValue.truth() == true else sqlCompare(operand!!, conditionValue) == 0) {
                            selected = value
                            break
                        }
                    }
                    selected?.let { eval(it, row, group) } ?: SqlValue.Null
                }
                is SqlExpr.Subquery -> {
                    val values = subquery(plan.subqueries.getValue(expression))
                    if (values.size > 1) throw SqlException(SqlErrorCode.TYPE, "Scalar subquery returned more than one row", expression.position)
                    values.singleOrNull()?.single() ?: SqlValue.Null
                }
                is SqlExpr.Exists -> SqlValue.Bool(subquery(plan.subqueries.getValue(expression)).isNotEmpty() != expression.negated)
                is SqlExpr.InQuery -> {
                    val value = eval(expression.operand, row, group)
                    var result: Boolean? = false
                    for (candidate in subquery(plan.subqueries.getValue(expression))) {
                        budget.step()
                        val comparison = sqlCompare(value, candidate.single())
                        if (comparison == 0) { result = true; break }
                        if (comparison == null) result = null
                    }
                    truthValue(result?.let { it != expression.negated })
                }
            }
        } catch (error: ArithmeticException) {
            throw SqlException(SqlErrorCode.ARITHMETIC, error.message ?: "SQL arithmetic failed", expression.position, error)
        } catch (error: IllegalArgumentException) {
            if (error is SqlException) throw error
            throw SqlException(SqlErrorCode.TYPE, error.message ?: "Invalid SQL value", expression.position, error)
        }
        return budget.checkValue(coerceSqlValue(result, plan.expressionTypes[expression]?.type ?: result.type))
    }

    private suspend fun binary(expression: SqlExpr.Binary, row: InputRow, group: List<InputRow>?): SqlValue {
        val left = eval(expression.left, row, group)
        if (expression.operator == "AND" && left.truth() == false) return SqlValue.Bool(false)
        if (expression.operator == "OR" && left.truth() == true) return SqlValue.Bool(true)
        val right = eval(expression.right, row, group)
        return when (expression.operator) {
            "AND" -> sqlAnd(left, right)
            "OR" -> sqlOr(left, right)
            "IS DISTINCT FROM", "IS NOT DISTINCT FROM" -> {
                val same = if (left == SqlValue.Null || right == SqlValue.Null) left == right else sqlCompare(left, right) == 0
                SqlValue.Bool(same == (expression.operator == "IS NOT DISTINCT FROM"))
            }
            "=", "!=", "<>", "<", "<=", ">", ">=" -> truthValue(sqlCompare(left, right)?.let {
                when (expression.operator) { "=" -> it == 0; "!=", "<>" -> it != 0; "<" -> it < 0; "<=" -> it <= 0; ">" -> it > 0; else -> it >= 0 }
            })
            "||" -> if (left == SqlValue.Null || right == SqlValue.Null) SqlValue.Null else {
                checkStringSize(left.text().length.toLong() + right.text().length)
                SqlValue.Text(left.text() + right.text())
            }
            else -> arithmetic(expression.operator, left, right)
        }
    }

    private fun arithmetic(operator: String, left: SqlValue, right: SqlValue): SqlValue {
        if (left == SqlValue.Null || right == SqlValue.Null) return SqlValue.Null
        if (left is SqlValue.Float64 && right is SqlValue.Float64) {
            if (right.value == 0.0 && operator in setOf("/", "%")) throw ArithmeticException("Division by zero")
            return SqlValue.Float64(when (operator) { "+" -> left.value + right.value; "-" -> left.value - right.value; "*" -> left.value * right.value; "/" -> left.value / right.value; "%" -> left.value % right.value; else -> error("Unknown arithmetic operator") })
        }
        val l = left.decimal()
        val r = right.decimal()
        if (r == Decimal.parse("0") && operator in setOf("/", "%")) throw ArithmeticException("Division by zero")
        return SqlValue.Exact(when (operator) {
            "+" -> l + r
            "-" -> l - r
            "*" -> l.multiplyRounded(r, minOf(18u, l.scale + r.scale))
            "/" -> l.divideRounded(r, maxOf(6u, l.scale, r.scale))
            "%" -> l % r
            else -> throw SqlException(SqlErrorCode.UNSUPPORTED, "Unsupported arithmetic operator '$operator'")
        })
    }

    private suspend fun aggregate(call: SqlExpr.Call, group: List<InputRow>): SqlValue {
        val name = call.name.normalized
        val argument = call.arguments.single()
        val seen = if (call.distinct) mutableSetOf<Any>() else null
        val memory = SqlMemory(budget)
        var count = 0L
        var value: SqlValue = SqlValue.Null
        var sum: SqlValue = SqlValue.Null
        try { for (row in group) {
            budget.step()
            if (call.filter != null && eval(call.filter, row).truth() != true) continue
            val current = if (argument is SqlExpr.Star) SqlValue.Int64(1) else eval(argument, row)
            if (current == SqlValue.Null) continue
            if (seen != null && !seen.add(sqlKey(current))) continue
            if (seen != null) memory.retain(64L + valueBytes(current))
            count++
            when (name) {
                "sum", "avg" -> sum = if (sum == SqlValue.Null) {
                    if (current is SqlValue.Float64) current else SqlValue.Exact(current.decimal())
                } else budget.checkValue(arithmetic("+", sum, current))
                "min" -> if (value == SqlValue.Null || sqlCompare(current, value)!! < 0) value = current
                "max" -> if (value == SqlValue.Null || sqlCompare(current, value)!! > 0) value = current
            }
        }
        return when (name) {
            "count" -> SqlValue.Int64(count)
            "sum" -> sum
            "avg" -> when (sum) {
                SqlValue.Null -> SqlValue.Null
                is SqlValue.Float64 -> SqlValue.Float64(sum.value / count)
                else -> SqlValue.Exact(sum.decimal().divideRounded(Decimal.parse(count.toString()), maxOf(6u, sum.decimal().scale)))
            }
            else -> value
        }
        } finally { memory.close() }
    }

    private suspend fun function(call: SqlExpr.Call, row: InputRow, group: List<InputRow>?): SqlValue {
        val name = call.name.normalized
        if (name == "coalesce") {
            for (argument in call.arguments) eval(argument, row, group).let { if (it != SqlValue.Null) return it }
            return SqlValue.Null
        }
        val arguments = call.arguments.map { eval(it, row, group) }
        if (name == "nullif") return if (sqlCompare(arguments[0], arguments[1]) == 0) SqlValue.Null else arguments[0]
        if (arguments.any { it == SqlValue.Null }) return SqlValue.Null
        val first = arguments.first()
        return when (name) {
            "lower" -> SqlValue.Text(first.text().lowercase())
            "upper" -> SqlValue.Text(first.text().uppercase())
            "length", "char_length" -> {
                var count = 0L
                var index = 0
                val text = first.text()
                while (index < text.length) {
                    budget.step()
                    index += if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) 2 else 1
                    count++
                }
                SqlValue.Int64(count)
            }
            "trim" -> SqlValue.Text(first.text().trim(' '))
            "ltrim" -> SqlValue.Text(first.text().trimStart(' '))
            "rtrim" -> SqlValue.Text(first.text().trimEnd(' '))
            "concat" -> {
                checkStringSize(arguments.sumOf { it.text().length.toLong() })
                SqlValue.Text(arguments.joinToString("") { it.text() })
            }
            "substring", "substr" -> {
                val memory = SqlMemory(budget)
                try {
                val chars = codePoints(first.text(), budget, memory)
                val start = exactLong(arguments[1], "Substring start")
                val length = arguments.getOrNull(2)?.let { exactLong(it, "Substring length") }
                if (length != null && length < 0) throw SqlException(SqlErrorCode.TYPE, "Substring length cannot be negative")
                val from = (start.coerceAtLeast(1) - 1).coerceAtMost(chars.size.toLong()).toInt()
                val end = if (length == null) chars.size else {
                    val sum = if (start > 0 && length > Long.MAX_VALUE - start) Long.MAX_VALUE else start + length
                    (sum.coerceAtLeast(1) - 1).coerceAtMost(chars.size.toLong()).toInt()
                }
                val until = maxOf(from, end)
                memory.retain(32L + (until - from) * 4L)
                SqlValue.Text(buildString {
                    for (index in from until until) { budget.step(); append(chars[index]) }
                })
                } finally { memory.close() }
            }
            "abs" -> if (first is SqlValue.Float64) SqlValue.Float64(abs(first.value)) else SqlValue.Exact(first.decimal().let { if (it < Decimal.parse("0")) -it else it })
            "floor", "ceil", "ceiling" -> if (first is SqlValue.Float64) SqlValue.Float64(if (name == "floor") floor(first.value) else ceil(first.value)) else {
                val decimal = first.decimal()
                val rounded = decimal.rescaleRounded(0u)
                SqlValue.Exact(when { name == "floor" && rounded > decimal -> rounded - Decimal.parse("1"); name != "floor" && rounded < decimal -> rounded + Decimal.parse("1"); else -> rounded })
            }
            "round" -> {
                val scale = arguments.getOrNull(1)?.let { exactLong(it, "Rounding scale") } ?: 0L
                if (scale !in 0..18) throw SqlException(SqlErrorCode.TYPE, "ROUND scale must be 0..18")
                if (first is SqlValue.Float64) {
                    val factor = 10.0.pow(scale.toInt())
                    SqlValue.Float64(round(first.value * factor) / factor)
                } else SqlValue.Exact(first.decimal().rescaleRounded(scale.toUInt()))
            }
            "date_part" -> datePart(first.text(), arguments[1])
            else -> throw SqlException(SqlErrorCode.UNSUPPORTED, "Unsupported function '$name'")
        }
    }

    private fun cast(value: SqlValue, expression: SqlExpr.Cast): SqlValue {
        if (value == SqlValue.Null) return value
        val type = castType(expression.type.name, expression)
        return when (type) {
            SqlType.TEXT -> SqlValue.Text(value.display())
            SqlType.BOOLEAN -> when (value) {
                is SqlValue.Bool -> value
                is SqlValue.Text -> when (asciiLower(value.value)) { "true" -> SqlValue.Bool(true); "false" -> SqlValue.Bool(false); else -> throw SqlException(SqlErrorCode.TYPE, "Invalid BOOLEAN text") }
                else -> throw SqlException(SqlErrorCode.TYPE, "Cannot cast ${value.type} to BOOLEAN")
            }
            SqlType.INT64 -> SqlValue.Int64(decimalForCast(value).rescaleExact(0u).toString().toLong())
            SqlType.UINT64 -> SqlValue.UInt64(decimalForCast(value).rescaleExact(0u).toString().toULong())
            SqlType.DECIMAL -> {
                val scale = expression.type.scale ?: if (expression.type.precision != null) 0 else null
                val decimal = decimalForCast(value).let {
                    if (scale == null) Decimal.parse(canonicalDecimal(it)) else it.rescaleRounded(scale.toUInt())
                }
                val precision = expression.type.precision
                if (precision != null && decimal.toString().removePrefix("-").replace(".", "").trimStart('0').length > precision) throw ArithmeticException("DECIMAL precision overflow")
                SqlValue.Exact(decimal)
            }
            SqlType.FLOAT64 -> when {
                value is SqlValue.Float64 -> value
                value is SqlValue.Text || value.type.isNumeric -> SqlValue.Float64(value.display().toDouble())
                else -> throw SqlException(SqlErrorCode.TYPE, "Cannot cast ${value.type} to FLOAT64")
            }
            SqlType.DATE -> if (value is SqlValue.Date) value else SqlValue.Date(LocalDate.parse(value.text()))
            SqlType.TIME -> if (value is SqlValue.Time) value else SqlValue.Time(LocalTime.parse(value.text()))
            SqlType.TIMESTAMP -> if (value is SqlValue.Timestamp) value else SqlValue.Timestamp(LocalDateTime.parse(value.text().replace(' ', 'T')))
            SqlType.BINARY -> if (value is SqlValue.Binary) value else SqlValue.Binary(Bytes(value.text()))
            else -> throw SqlException(SqlErrorCode.TYPE, "Unsupported conversion to $type")
        }
    }

    private fun decimalForCast(value: SqlValue): Decimal = when {
        value is SqlValue.Text -> Decimal.parse(value.value)
        value is SqlValue.Float64 -> Decimal.parse(expandScientific(value.value.toString()))
        value.type.isNumeric -> value.decimal()
        else -> throw SqlException(SqlErrorCode.TYPE, "Cannot cast ${value.type} to an exact number")
    }

    private fun checkStringSize(characters: Long) {
        if (characters > (budget.options.maxBufferedBytes - 32) / 2) budget.exceeded("String size")
    }
}

internal fun SqlValue.text(): String = (this as? SqlValue.Text)?.value ?: throw SqlException(SqlErrorCode.TYPE, "Expected TEXT, found $type")

internal fun exactLong(value: SqlValue, label: String): Long = try {
    if (value is SqlValue.Float64) throw SqlException(SqlErrorCode.TYPE, "$label requires an exact integer")
    value.decimal().rescaleExact(0u).toString().toLong()
} catch (error: IllegalArgumentException) {
    if (error is SqlException) throw error
    throw SqlException(SqlErrorCode.TYPE, "$label is outside the signed integer range", cause = error)
} catch (error: ArithmeticException) {
    throw SqlException(SqlErrorCode.TYPE, "$label requires an integer", cause = error)
}

private fun datePart(part: String, value: SqlValue): SqlValue {
    val date = when (value) { is SqlValue.Date -> value.value; is SqlValue.Timestamp -> value.value.date; else -> null }
    val time = when (value) { is SqlValue.Time -> value.value; is SqlValue.Timestamp -> value.value.time; else -> null }
    val result = when (asciiLower(part)) {
        "year" -> date?.year
        "month" -> date?.month?.ordinal?.plus(1)
        "day" -> date?.day
        "hour" -> time?.hour
        "minute" -> time?.minute
        "second" -> time?.second
        else -> throw SqlException(SqlErrorCode.UNSUPPORTED, "Unknown date part '$part'")
    } ?: throw SqlException(SqlErrorCode.TYPE, "Date part '$part' does not apply to ${value.type}")
    return SqlValue.Int64(result.toLong())
}

/** Code-point iteration keeps LIKE '_' and substring boundaries independent of UTF-16 surrogates. */
private suspend fun codePoints(text: String, budget: SqlBudget, memory: SqlMemory): List<String> = buildList {
    var index = 0
    while (index < text.length) {
        budget.step()
        val width = if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) 2 else 1
        memory.retain(32L + width * 2L)
        add(text.substring(index, index + width))
        index += width
    }
}

private suspend fun like(text: String, pattern: String, escape: String?, budget: SqlBudget): Boolean {
    val memory = SqlMemory(budget)
    try {
    val escapePoint = escape?.let { codePoints(it, budget, memory).singleOrNull() ?: throw SqlException(SqlErrorCode.TYPE, "LIKE ESCAPE must contain one character") }
    val patternPoints = codePoints(pattern, budget, memory)
    val tokens = mutableListOf<Pair<String, Boolean>>()
    var index = 0
    while (index < patternPoints.size) {
        budget.step()
        memory.retain(24L)
        val point = patternPoints[index++]
        if (point == escapePoint) {
            if (index == patternPoints.size) throw SqlException(SqlErrorCode.TYPE, "LIKE pattern ends with ESCAPE")
            tokens += patternPoints[index++] to true
        } else tokens += point to false
    }
    val characters = codePoints(text, budget, memory)
    var input = 0
    var token = 0
    var wildcard = -1
    var retryInput = 0
    while (input < characters.size) {
        budget.step()
        val current = tokens.getOrNull(token)
        when {
            current != null && !current.second && current.first == "%" -> { wildcard = token++; retryInput = input }
            current != null && ((!current.second && current.first == "_") || current.first == characters[input]) -> { input++; token++ }
            wildcard >= 0 -> { token = wildcard + 1; input = ++retryInput }
            else -> return false
        }
    }
    while (token < tokens.size && tokens[token] == ("%" to false)) { budget.step(); token++ }
    return token == tokens.size
    } finally { memory.close() }
}

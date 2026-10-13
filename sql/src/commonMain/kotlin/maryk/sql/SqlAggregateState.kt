package maryk.sql

import maryk.core.properties.types.Decimal
import maryk.sql.syntax.SqlExpr

/** One bounded accumulator per group and aggregate; input records are never retained. */
internal class SqlAggregateState(private val call: SqlExpr.Call, private val evaluator: SqlEvaluator, private val budget: SqlBudget) {
    private val memory = SqlMemory(budget)
    private val seen = if (call.distinct) mutableSetOf<Any>() else null
    private var count = 0L
    private var value: SqlValue = SqlValue.Null
    private var sum: SqlValue = SqlValue.Null
    private var stateBytes = 0L

    suspend fun add(row: InputRow) {
        budget.step()
        if (call.filter != null && evaluator.eval(call.filter, row).truth() != true) return
        val argument = call.arguments.single()
        val current = if (argument is SqlExpr.Star) SqlValue.Int64(1) else evaluator.eval(argument, row)
        if (current == SqlValue.Null) return
        if (seen != null) {
            if (!seen.add(sqlKey(current))) return
            memory.retain(64L + valueBytes(current))
        }
        count++
        when (call.name.normalized) {
            "sum", "avg" -> sum = if (sum == SqlValue.Null) {
                if (current is SqlValue.Float64) current else SqlValue.Exact(current.decimal())
            } else try { budget.checkValue(evaluator.arithmetic("+", sum, current)) }
            catch (error: ArithmeticException) { throw SqlException(SqlErrorCode.ARITHMETIC, error.message ?: "Aggregate overflow", call.position, error) }
            "min" -> if (value == SqlValue.Null || sqlCompare(current, value)!! < 0) value = current
            "max" -> if (value == SqlValue.Null || sqlCompare(current, value)!! > 0) value = current
        }
        memory.release(stateBytes)
        stateBytes = 64L + valueBytes(value) + valueBytes(sum)
        memory.retain(stateBytes)
    }

    fun result(): SqlValue = when (call.name.normalized) {
        "count" -> SqlValue.Int64(count)
        "sum" -> sum
        "avg" -> when (val total = sum) {
            SqlValue.Null -> SqlValue.Null
            is SqlValue.Float64 -> SqlValue.Float64(total.value / count)
            else -> {
                val decimal = total.decimal()
                SqlValue.Exact(decimal.divideRounded(Decimal.parse(count.toString()), maxOf(6u, decimal.scale)))
            }
        }
        else -> value
    }

    fun close() = memory.close()
}

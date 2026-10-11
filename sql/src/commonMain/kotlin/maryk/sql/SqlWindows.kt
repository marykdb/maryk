package maryk.sql

import maryk.sql.syntax.SqlBoundKind
import maryk.sql.syntax.SqlFrameUnit
import maryk.sql.syntax.SqlWindowBound
import maryk.sql.syntax.SqlExpr
import maryk.sql.syntax.SqlWindowFrame

private data class WindowRow(val index: Int, val row: InputRow, val order: List<SqlValue>)

/** Window operators share the normal execution, expression and logical memory budgets. */
internal suspend fun calculateWindows(
    windows: List<SqlExpr.Window>, rows: List<InputRow>, evaluator: SqlEvaluator, budget: SqlBudget, memory: SqlMemory,
): Map<SqlExpr.Window, List<SqlValue>> {
    val results = linkedMapOf<SqlExpr.Window, List<SqlValue>>()
    for (window in windows) {
        val scratch = SqlMemory(budget)
        try {
            val partitions = linkedMapOf<List<Any>, MutableList<WindowRow>>()
            val values = MutableList<SqlValue>(rows.size) { SqlValue.Null }
            memory.retain(rows.size * 8L)
            for ((index, row) in rows.withIndex()) {
                budget.step()
                val partitionValues = window.partitionBy.map { evaluator.eval(it, row) }
                val key = partitionValues.map(::sqlKey)
                val partition = partitions.getOrPut(key) {
                    if (partitions.size >= budget.options.maxGroups) budget.exceeded("windowPartitions", budget.options.maxGroups.toLong(), partitions.size.toLong() + 1)
                    scratch.retain(96L + partitionValues.sumOf(::valueBytes))
                    mutableListOf()
                }
                val order = window.orderBy.map { evaluator.eval(it.expression, row) }
                scratch.retain(64L + order.sumOf(::valueBytes))
                partition += WindowRow(index, row, order)
            }
            suspend fun assign(row: WindowRow, value: SqlValue) {
                budget.step()
                memory.retain(valueBytes(value))
                values[row.index] = budget.checkValue(value)
            }
            for (partition in partitions.values) {
                val ordered = if (window.orderBy.isEmpty()) partition else sqlStableSort(partition, budget) { a, b -> compareOrderValues(a.order, b.order, window.orderBy) }
                val name = window.call.name.normalized
                val startOffset = window.frame.start.offset?.let { evaluator.eval(it) }
                val endOffset = window.frame.end.offset?.let { evaluator.eval(it) }
                for (offset in listOfNotNull(startOffset, endOffset)) {
                    if (window.frame.unit == SqlFrameUnit.ROWS) nonNegative(offset, "windowFrameOffset")
                    else if (offset == SqlValue.Null || !offset.type.isNumeric || sqlCompare(offset, if (offset is SqlValue.Float64) SqlValue.Float64(0.0) else SqlValue.Int64(0))!! < 0) throw SqlException(SqlErrorCode.TYPE, "RANGE offset must be a nonnegative number")
                }
                suspend fun boundary(index: Int, bound: SqlWindowBound, offset: SqlValue?, start: Boolean): Int {
                    when (bound.kind) {
                        SqlBoundKind.UNBOUNDED_PRECEDING -> return 0
                        SqlBoundKind.UNBOUNDED_FOLLOWING -> return ordered.lastIndex
                        else -> Unit
                    }
                    if (window.frame.unit == SqlFrameUnit.ROWS) {
                        val distance = offset?.let { nonNegative(it, "windowFrameOffset").coerceAtMost(ordered.size.toLong() + 1) } ?: 0L
                        val target = index.toLong() + if (bound.kind == SqlBoundKind.PRECEDING) -distance else distance
                        return target.coerceIn(if (start) 0L else -1L, if (start) ordered.size.toLong() else ordered.lastIndex.toLong()).toInt()
                    }
                    val order = ordered[index].order
                    var threshold = order
                    if (offset != null && order.single() != SqlValue.Null) {
                        val value = order.single()
                        val amount = if (value is SqlValue.Float64 && offset !is SqlValue.Float64) {
                            val floating = offset.decimal().toString().toDouble()
                            if (!floating.isFinite()) throw SqlException(SqlErrorCode.ARITHMETIC, "RANGE offset exceeds floating numeric range", window.position)
                            SqlValue.Float64(floating)
                        } else offset
                        val subtract = (bound.kind == SqlBoundKind.PRECEDING) == window.orderBy.single().ascending
                        threshold = listOf(try { budget.checkValue(evaluator.arithmetic(if (subtract) "-" else "+", value, amount)) }
                            catch (error: ArithmeticException) { throw SqlException(SqlErrorCode.ARITHMETIC, error.message ?: "RANGE boundary overflow", window.position, error) })
                    }
                    var low = 0
                    var high = ordered.size
                    while (low < high) {
                        budget.step()
                        val middle = low + (high - low) / 2
                        val comparison = compareOrderValues(ordered[middle].order, threshold, window.orderBy)
                        if (comparison < 0 || !start && comparison == 0) low = middle + 1 else high = middle
                    }
                    return if (start) low else low - 1
                }
                when (name) {
                    "row_number", "rank", "dense_rank" -> {
                        var rank = 1L
                        var dense = 1L
                        for ((index, row) in ordered.withIndex()) {
                            if (index > 0 && compareOrderValues(ordered[index - 1].order, row.order, window.orderBy) != 0) { rank = index.toLong() + 1; dense++ }
                            assign(row, SqlValue.Int64(when (name) { "row_number" -> index.toLong() + 1; "rank" -> rank; else -> dense }))
                        }
                    }
                    "ntile" -> {
                        val buckets = nonNegative(evaluator.eval(window.call.arguments.single()), "windowBuckets")
                        if (buckets == 0L) throw SqlException(SqlErrorCode.TYPE, "NTILE requires a positive bucket count")
                        val count = ordered.size.toLong()
                        val width = count / buckets
                        val larger = count % buckets
                        val split = (width + 1) * larger
                        for ((index, row) in ordered.withIndex()) {
                            val bucket = if (index < split) index / (width + 1) + 1 else (index - split) / width + larger + 1
                            assign(row, SqlValue.Int64(bucket))
                        }
                    }
                    "first_value", "last_value", "nth_value" -> {
                        val nth = if (name == "nth_value") nonNegative(evaluator.eval(window.call.arguments[1]), "windowNth") else 1L
                        if (nth == 0L) throw SqlException(SqlErrorCode.TYPE, "NTH_VALUE requires a positive index")
                        for ((index, row) in ordered.withIndex()) {
                            val start = boundary(index, window.frame.start, startOffset, true)
                            val end = boundary(index, window.frame.end, endOffset, false)
                            val target = if (name == "last_value") end.toLong() else if (nth > ordered.size) ordered.size.toLong() else start.toLong() + nth - 1
                            assign(row, if (target >= start && target <= end && target in 0L until ordered.size.toLong()) evaluator.eval(window.call.arguments[0], ordered[target.toInt()].row) else SqlValue.Null)
                        }
                    }
                    "lag", "lead" -> {
                        val offset = window.call.arguments.getOrNull(1)?.let { nonNegative(evaluator.eval(it), "windowOffset") } ?: 1L
                        for ((index, row) in ordered.withIndex()) {
                            val target = if (name == "lag") index.toLong() - offset else if (offset > Long.MAX_VALUE - index) Long.MAX_VALUE else index + offset
                            val value = if (target in 0L until ordered.size.toLong()) evaluator.eval(window.call.arguments[0], ordered[target.toInt()].row)
                            else window.call.arguments.getOrNull(2)?.let { evaluator.eval(it, row.row) } ?: SqlValue.Null
                            assign(row, value)
                        }
                    }
                    else -> {
                        val state = SqlAggregateState(window.call, evaluator, budget)
                        try {
                            if (window.frame.start.kind == SqlBoundKind.UNBOUNDED_PRECEDING && window.frame.end.kind == SqlBoundKind.UNBOUNDED_FOLLOWING || window.frame == SqlWindowFrame.DEFAULT && window.orderBy.isEmpty()) {
                                for (row in ordered) state.add(row.row)
                                val value = state.result()
                                for (row in ordered) assign(row, value)
                            } else if (window.frame == SqlWindowFrame.DEFAULT || window.frame == SqlWindowFrame.RUNNING_ROWS) {
                                var start = 0
                                while (start < ordered.size) {
                                    var end = start + 1
                                    if (window.frame == SqlWindowFrame.DEFAULT) while (end < ordered.size && compareOrderValues(ordered[start].order, ordered[end].order, window.orderBy) == 0) { budget.step(); end++ }
                                    for (index in start until end) state.add(ordered[index].row)
                                    val value = state.result()
                                    for (index in start until end) assign(ordered[index], value)
                                    start = end
                                }
                            } else {
                                // General frames use the same aggregate contract, with every visited row charged to the work budget.
                                for ((index, row) in ordered.withIndex()) {
                                    val start = boundary(index, window.frame.start, startOffset, true)
                                    val end = boundary(index, window.frame.end, endOffset, false)
                                    val frame = SqlAggregateState(window.call, evaluator, budget)
                                    try {
                                        for (target in start..end) frame.add(ordered[target].row)
                                        assign(row, frame.result())
                                    } finally { frame.close() }
                                }
                            }
                        } finally { state.close() }
                    }
                }
            }
            results[window] = values
        } finally { scratch.close() }
    }
    return results
}

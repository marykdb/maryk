package maryk.sql

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.graph
import maryk.core.properties.types.Key
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.datastore.shared.captureSnapshotVersion
import maryk.sql.syntax.SqlExpr
import maryk.sql.syntax.SqlJoinType
import maryk.sql.syntax.SqlOrder
import maryk.sql.syntax.SqlSetOperator

/** All nested relations share one cache, consistency boundary and execution budget. */
internal class SqlRuntime(
    private val sql: MarykSql,
    private val parameters: List<SqlValue>,
    private val readOptions: SqlReadOptions,
    private val budget: SqlBudget,
) {
    private val cache = mutableMapOf<BoundQuery, List<List<SqlValue>>>()
    private var snapshotCaptured = false

    private fun evaluator(plan: BoundQuery) = SqlEvaluator(plan, parameters, budget) { materialize(it) }

    suspend fun execute(plan: BoundQuery, emit: suspend (List<SqlValue>) -> Unit) {
        // Validate constant expression/parameter failures in every relation before the first read.
        val seen = mutableSetOf<BoundQuery>()
        suspend fun preflight(current: BoundQuery) {
            if (!seen.add(current)) return
            val evaluator = evaluator(current)
            val query = current.query
            val roots = query.select.map { it.expression } + query.groupBy + query.orderBy.map { it.expression } +
                listOfNotNull(query.where, query.having, query.limit, query.offset) + current.joins.mapNotNull { it.condition } + query.values.orEmpty().flatten()
            for (expression in roots) {
                if (!containsColumn(expression) && !containsSubquery(expression) && !containsAggregate(expression)) evaluator.eval(expression)
            }
            current.children.forEach { preflight(it) }
        }
        preflight(plan)
        produce(plan) { values -> budget.returned(); emit(values); true }
    }

    private suspend fun snapshot() {
        if (snapshotCaptured) return
        snapshotCaptured = true
        budget.snapshotVersion = try {
            when (val consistency = readOptions.consistency) {
                SqlConsistency.Current -> null
                SqlConsistency.Snapshot -> sql.dataStore.captureSnapshotVersion()
                is SqlConsistency.AtVersion -> {
                    val watermark = sql.dataStore.captureSnapshotVersion()
                    if (consistency.version > watermark) throw SqlException(SqlErrorCode.CONSISTENCY, "Requested version is newer than the authoritative store watermark")
                    consistency.version
                }
            }
        } catch (error: RequestException) {
            throw SqlException(SqlErrorCode.CONSISTENCY, error.message ?: "Historical reads are unavailable", cause = error)
        }
    }

    private suspend fun materialize(plan: BoundQuery): List<List<SqlValue>> {
        cache[plan]?.let { return it }
        val rows = mutableListOf<List<SqlValue>>()
        produce(plan) { values ->
            budget.retain(64L + values.sumOf(::valueBytes))
            rows += values
            true
        }
        cache[plan] = rows
        return rows
    }

    private suspend fun produce(plan: BoundQuery, emit: suspend (List<SqlValue>) -> Boolean): Boolean {
        val query = plan.query
        val evaluator = evaluator(plan)
        val limit = query.limit?.let { nonNegative(evaluator.eval(it), "LIMIT") } ?: Long.MAX_VALUE
        val offset = query.offset?.let { nonNegative(evaluator.eval(it), "OFFSET") } ?: 0L
        if (limit == 0L) return true
        val memory = SqlMemory(budget)
        try {
            val retained = mutableListOf<InputRow>()
            val outputs = mutableListOf<OutputRow>()
            val blocking = plan.aggregate || query.orderBy.isNotEmpty() || query.distinct
            var passed = 0L
            var produced = 0L
            var downstream = true
            suspend fun deliver(values: List<SqlValue>): Boolean {
                budget.step()
                if (passed++ < offset) return true
                if (produced >= limit) return false
                produced++
                downstream = emit(values)
                return downstream && produced < limit
            }
            suspend fun projected(values: List<SqlValue>, row: InputRow, group: List<InputRow>? = null): Boolean {
                val normalized = values.zip(plan.columns).map { (value, column) -> coerceSqlValue(value, column.type) }
                if (!blocking) return deliver(normalized)
                val order = query.orderBy.map { evaluator.eval(it.expression, row, group) }
                memory.retain(96L + normalized.sumOf(::valueBytes) + order.sumOf(::valueBytes))
                outputs += OutputRow(normalized, order)
                return true
            }
            when {
                query.values != null || plan.setOperands != null -> {
                    suspend fun accept(values: List<SqlValue>): Boolean {
                        val normalized = values.zip(plan.columns).map { (value, column) -> coerceSqlValue(value, column.type) }
                        val row = plan.sources.single().columns.associateWith { normalized[it.outputIndex!!] }
                        return projected(normalized, row)
                    }
                    if (query.values != null) {
                        for (expressions in query.values) {
                            budget.step()
                            if (!accept(expressions.map { evaluator.eval(it) })) break
                        }
                    } else {
                        val operands = plan.setOperands!!
                        val operation = query.setOperation!!
                        if (operation.operator == SqlSetOperator.UNION && operation.all) {
                            var keepReading = true
                            produce(operands.first) { values -> accept(values).also { keepReading = it } }
                            if (keepReading) produce(operands.second) { values -> accept(values) }
                        } else {
                            val left = materialize(operands.first)
                            val right = materialize(operands.second)
                            val counts = mutableMapOf<List<Any>, Int>()
                            val seen = mutableSetOf<List<Any>>()
                            if (operation.operator != SqlSetOperator.UNION) for (values in right) {
                                budget.step()
                                val key = values.map(::sqlKey)
                                if (key !in counts) memory.retain(64L + values.sumOf(::valueBytes))
                                counts[key] = (counts[key] ?: 0) + 1
                            }
                            suspend fun setRow(values: List<SqlValue>): Boolean {
                                budget.step()
                                val key = values.map(::sqlKey)
                                val count = counts[key] ?: 0
                                val include = when (operation.operator) {
                                    SqlSetOperator.UNION -> true
                                    SqlSetOperator.INTERSECT -> count > 0
                                    SqlSetOperator.EXCEPT -> count == 0
                                }
                                if (operation.all && count > 0) counts[key] = count - 1
                                if (!include) return true
                                if (!operation.all) {
                                    if (!seen.add(key)) return true
                                    memory.retain(64L + values.sumOf(::valueBytes))
                                }
                                return accept(values)
                            }
                            var more = true
                            for (values in left) if (!setRow(values)) { more = false; break }
                            if (more && operation.operator == SqlSetOperator.UNION) for (values in right) if (!setRow(values)) break
                        }
                    }
                }
                else -> {
                    suspend fun consume(row: InputRow): Boolean {
                        budget.step()
                        if (query.where != null && evaluator.eval(query.where, row).truth() != true) return true
                        if (plan.aggregate) {
                            memory.retain(128L + row.values.sumOf(::valueBytes))
                            retained += row
                            return true
                        }
                        return projected(query.select.map { evaluator.eval(it.expression, row) }, row)
                    }
                    val constantFalse = query.where != null && !containsColumn(query.where) && evaluator.eval(query.where).truth() != true
                    if (!constantFalse) {
                        if (plan.sources.isEmpty()) consume(emptyMap()) else joinRows(plan, evaluator, memory, ::consume)
                    }
                    if (plan.aggregate) {
                        suspend fun projectGroup(group: List<InputRow>) {
                            val row = group.firstOrNull() ?: emptyMap()
                            if (query.having != null && evaluator.eval(query.having, row, group).truth() != true) return
                            projected(query.select.map { evaluator.eval(it.expression, row, group) }, row, group)
                        }
                        if (query.groupBy.isEmpty()) projectGroup(retained) else {
                            val groups = linkedMapOf<List<Any>, MutableList<InputRow>>()
                            for (row in retained) {
                                budget.step()
                                val values = query.groupBy.map { evaluator.eval(it, row) }
                                val key = values.map(::sqlKey)
                                val group = groups.getOrPut(key) {
                                    if (groups.size >= sql.options.maxGroups) budget.exceeded("Group count")
                                    memory.retain(96L + values.sumOf(::valueBytes))
                                    mutableListOf()
                                }
                                memory.retain(8L)
                                group += row
                            }
                            for (group in groups.values) projectGroup(group)
                        }
                    }
                }
            }
            if (!blocking) return downstream
            var result: List<OutputRow> = outputs
            if (query.distinct) {
                val seen = mutableSetOf<List<Any>>()
                val unique = mutableListOf<OutputRow>()
                for (row in result) {
                    budget.step()
                    if (seen.add(row.values.map(::sqlKey))) {
                        memory.retain(64L + row.values.sumOf(::valueBytes))
                        unique += row
                    }
                }
                result = unique
            }
            if (query.orderBy.isNotEmpty()) result = mergeSort(result, query.orderBy, budget)
            for (row in result) if (!deliver(row.values)) break
            return downstream
        } finally { memory.close() }
    }

    private suspend fun sourceRows(plan: BoundQuery, source: BoundSource, evaluator: SqlEvaluator, emit: suspend (InputRow) -> Boolean): Boolean {
        val relation = source.relation
        if (relation != null) {
            for (values in materialize(relation)) {
                budget.step()
                if (!emit(source.columns.associateWith { values[it.outputIndex!!] })) return false
            }
            return true
        }
        val table = source.table!!
        val inputs = plan.inputs.filter { it.sourceId == source.id }
        val rootIndices = inputs.mapNotNull { it.indices.firstOrNull() }.distinct()
        val select = table.model.graph { rootIndices.map { this[it]!! } }
        val access = nativeAccess(plan, source)
        val where = access.filter(table, evaluator)
        if (access.keyExpressions != null) {
            val keys = linkedSetOf<Key<IsRootDataModel>>()
            val memory = SqlMemory(budget)
            try {
                for (expression in access.keyExpressions) {
                    val value = evaluator.eval(expression)
                    if (value == SqlValue.Null) continue
                    val binding = source.columns.single { it.indices.isEmpty() && it.outputIndex == null }
                    val key = domainValue(value, binding, SqlErrorCode.TYPE) as SqlValue.Key
                    if (keys.add(Key(key.value.bytes))) memory.retain(64L + key.value.size)
                }
                for (batch in keys.toList().chunked(sql.options.pageSize)) {
                    budget.checkpoint()
                    snapshot()
                    budget.request()
                    val page = sql.dataStore.execute(table.model.get(*batch.toTypedArray(), select = select, where = where, toVersion = budget.snapshotVersion, filterSoftDeleted = readOptions.filterSoftDeleted))
                    budget.fetched(page.values.size)
                    for (record in page.values) {
                        budget.step()
                        if (!emit(inputs.associateWith { budget.checkValue(it.read(record)) })) return false
                    }
                }
                return true
            } finally { memory.close() }
        }
        if (!sql.options.allowTableScan && where == null) throw SqlException(SqlErrorCode.UNSUPPORTED, "This query requires a table scan; enable SqlOptions.allowTableScan with appropriate budgets")
        var cursor: ScanCursor? = null
        do {
            budget.checkpoint()
            snapshot()
            budget.request()
            val page = try {
                sql.dataStore.execute(table.model.scan(select = select, where = where, limit = sql.options.pageSize.toUInt(), cursor = cursor,
                    toVersion = budget.snapshotVersion, filterSoftDeleted = readOptions.filterSoftDeleted, allowTableScan = sql.options.allowTableScan))
            } catch (error: StorageException) {
                if (!sql.options.allowTableScan && error.message?.contains("minimum key scan bytes") == true) throw SqlException(SqlErrorCode.UNSUPPORTED, "No fitting indexed/key access path; table scans are disabled", cause = error)
                throw error
            }
            budget.fetched(page.values.size)
            for (record in page.values) {
                budget.step()
                if (!emit(inputs.associateWith { budget.checkValue(it.read(record)) })) return false
            }
            cursor = page.nextCursor
        } while (cursor != null)
        return true
    }

    private suspend fun joinRows(plan: BoundQuery, evaluator: SqlEvaluator, memory: SqlMemory, emit: suspend (InputRow) -> Boolean) {
        if (plan.joins.isEmpty()) { sourceRows(plan, plan.sources.first(), evaluator, emit); return }
        val rightRows = mutableListOf<List<InputRow>>()
        val keys = mutableListOf<List<Pair<CatalogColumn, CatalogColumn>>>()
        val hashes = mutableListOf<Map<List<Any>, List<InputRow>>?>()
        for (join in plan.joins) {
            val rows = mutableListOf<InputRow>()
            sourceRows(plan, join.source, evaluator) { row ->
                memory.retain(128L + row.values.sumOf(::valueBytes))
                rows += row
                true
            }
            rightRows += rows
            val pairs = mutableListOf<Pair<CatalogColumn, CatalogColumn>>()
            fun equi(expression: SqlExpr) {
                if (expression is SqlExpr.Binary && expression.operator == "AND") { equi(expression.left); equi(expression.right) }
                if (expression is SqlExpr.Binary && expression.operator == "=") {
                    val l = (expression.left as? SqlExpr.Column)?.let { plan.references[it] }
                    val r = (expression.right as? SqlExpr.Column)?.let { plan.references[it] }
                    if (l != null && r != null) {
                        if (l.sourceId == join.source.id && r.sourceId != join.source.id) pairs += r to l
                        if (r.sourceId == join.source.id && l.sourceId != join.source.id) pairs += l to r
                    }
                }
            }
            join.condition?.let(::equi)
            keys += pairs
            if (pairs.isEmpty()) hashes += null else {
                val hash = mutableMapOf<List<Any>, MutableList<InputRow>>()
                for (row in rows) {
                    budget.step()
                    val values = pairs.map { row[it.second] ?: SqlValue.Null }
                    if (values.any { it == SqlValue.Null }) continue
                    val key = values.map(::sqlKey)
                    val bucket = hash.getOrPut(key) { memory.retain(64L + values.sumOf(::valueBytes)); mutableListOf() }
                    memory.retain(8L)
                    bucket += row
                }
                hashes += hash
            }
        }
        suspend fun expand(row: InputRow, index: Int): Boolean {
            budget.step()
            if (index == plan.joins.size) return emit(row)
            val join = plan.joins[index]
            val hash = hashes[index]
            val matches = if (hash == null) rightRows[index] else {
                val values = keys[index].map { row[it.first] ?: SqlValue.Null }
                if (values.any { it == SqlValue.Null }) emptyList() else hash[values.map(::sqlKey)].orEmpty()
            }
            var matched = false
            for (right in matches) {
                budget.step()
                val combined = row + right
                if (join.condition != null && evaluator.eval(join.condition, combined).truth() != true) continue
                matched = true
                if (!expand(combined, index + 1)) return false
            }
            if (!matched && join.type == SqlJoinType.LEFT) {
                return expand(row + join.source.columns.associateWith { SqlValue.Null }, index + 1)
            }
            return true
        }
        sourceRows(plan, plan.sources.first(), evaluator) { expand(it, 0) }
    }
}

internal class SqlMemory(private val budget: SqlBudget) {
    private var retained = 0L
    fun retain(bytes: Long) { budget.retain(bytes); retained += bytes }
    fun close() { budget.release(retained); retained = 0 }
}

internal fun nonNegative(value: SqlValue, label: String): Long = exactLong(value, label).also {
    if (it < 0) throw SqlException(SqlErrorCode.TYPE, "$label cannot be negative")
}

private data class OutputRow(val values: List<SqlValue>, val order: List<SqlValue>)

/** A cooperative stable merge sort avoids an uninterruptible platform sort on browser targets. */
private suspend fun mergeSort(rows: List<OutputRow>, orders: List<SqlOrder>, budget: SqlBudget): List<OutputRow> {
    if (rows.size < 2) return rows
    budget.retain(rows.size * 16L)
    try {
        var source = rows.toMutableList()
        var target = rows.toMutableList()
        var width = 1
        while (width < rows.size) {
            var start = 0
            while (start < rows.size) {
                val middle = minOf(start + width, rows.size)
                val end = minOf(start + 2 * width, rows.size)
                var left = start
                var right = middle
                for (output in start until end) {
                    budget.step()
                    target[output] = when {
                        left >= middle -> source[right++]
                        right >= end -> source[left++]
                        compareRows(source[left], source[right], orders) <= 0 -> source[left++]
                        else -> source[right++]
                    }
                }
                start = end
            }
            val previous = source
            source = target
            target = previous
            width *= 2
        }
        return source
    } finally { budget.release(rows.size * 16L) }
}

private fun compareRows(left: OutputRow, right: OutputRow, orders: List<SqlOrder>): Int {
    for (index in orders.indices) {
        val l = left.order[index]
        val r = right.order[index]
        val order = orders[index]
        val comparison = if (l == SqlValue.Null || r == SqlValue.Null) {
            val nullsFirst = order.nullsFirst ?: !order.ascending
            when { l == r -> 0; l == SqlValue.Null -> if (nullsFirst) -1 else 1; else -> if (nullsFirst) 1 else -1 }
        } else sqlCompare(l, r)!!.let { if (order.ascending) it else -it }
        if (comparison != 0) return comparison
    }
    return 0
}

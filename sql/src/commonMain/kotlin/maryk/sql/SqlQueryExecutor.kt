package maryk.sql

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.graph
import maryk.core.properties.types.Key
import maryk.core.query.orders.Order
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.types.Bytes
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.datastore.shared.captureSnapshotVersion
import maryk.sql.syntax.LiteralKind
import maryk.sql.syntax.SqlExpr
import maryk.sql.syntax.SqlJoinType
import maryk.sql.syntax.SqlOrder
import maryk.sql.syntax.SqlSetOperator

private enum class QueryOutput { VALUES, EXISTS, ROW_COUNT }

/** All nested relations share one cache, consistency boundary and execution budget. */
internal class SqlRuntime(
    private val sql: MarykSql,
    private val parameters: List<SqlValue>,
    private val readOptions: SqlReadOptions,
    private val budget: SqlBudget,
) {
    private val cache = mutableMapOf<BoundQuery, List<List<SqlValue>>>()
    private val existenceCache = mutableMapOf<BoundQuery, Boolean>()
    private var snapshotCaptured = false
    private val preflighted = mutableSetOf<Pair<BoundQuery, QueryOutput>>()

    private fun evaluator(plan: BoundQuery, outerRow: InputRow = emptyMap()) = SqlEvaluator(plan, parameters, budget,
        subquery = { child, row -> subqueryRows(child, row) }, exists = { child, row -> exists(child, row) }, outerRow = outerRow)

    private suspend fun subqueryRows(plan: BoundQuery, outerRow: InputRow): SqlSubqueryRows {
        if (plan.externalColumns().isEmpty()) return SqlSubqueryRows(materialize(plan))
        val memory = SqlMemory(budget)
        val rows = mutableListOf<List<SqlValue>>()
        try {
            produce(plan, outerRow = outerRow) { values -> memory.retain(64L + values.sumOf(::valueBytes)); rows += values; true }
            return SqlSubqueryRows(rows, memory::close)
        } catch (error: Throwable) { memory.close(); throw error }
    }

    suspend fun execute(plan: BoundQuery, emit: suspend (List<SqlValue>) -> Unit) {
        preflight(plan)
        produce(plan) { values -> budget.returned(); emit(values); true }
    }

    private suspend fun preflight(current: BoundQuery, output: QueryOutput = QueryOutput.VALUES, outerRow: InputRow = emptyMap()) {
        if (!preflighted.add(current to output)) return
        val evaluator = evaluator(current, outerRow)
        val query = current.query
        val offset = query.offset?.let { nonNegative(evaluator.eval(it), "OFFSET") } ?: 0L
        val discardProjection = output != QueryOutput.VALUES && (!query.distinct || output == QueryOutput.EXISTS && offset == 0L)
        val childOutput = childOutput(output, discardProjection, offset)
        val roots = expressions(current, discardProjection, output != QueryOutput.VALUES)
        for (expression in roots) {
            if (!containsColumn(expression) && !containsSubquery(expression) && !containsAggregate(expression) && !containsWindow(expression)) evaluator.eval(expression)
        }
        // Expression subqueries are preflighted by produce only when their branch runs.
        // Walking them here would evaluate unused CASE/COALESCE/AND/OR branches.
        current.sources.mapNotNull { it.relation }.forEach { preflight(it) }
        current.body?.let { preflight(it, childOutput) }
        current.setOperands?.let { (left, right) ->
            val operandOutput = if (query.setOperation?.let { it.operator == SqlSetOperator.UNION && it.all } == true) childOutput else QueryOutput.VALUES
            preflight(left, operandOutput)
            preflight(right, operandOutput)
        }
    }

    private fun childOutput(output: QueryOutput, discardProjection: Boolean, offset: Long): QueryOutput = when {
        !discardProjection -> QueryOutput.VALUES
        output == QueryOutput.ROW_COUNT || offset > 0L -> QueryOutput.ROW_COUNT
        else -> QueryOutput.EXISTS
    }

    private fun expressions(plan: BoundQuery, discardProjection: Boolean, discardOrdering: Boolean): List<SqlExpr> {
        val query = plan.query
        return query.groupBy + listOfNotNull(query.where, query.having, query.limit, query.offset) +
            plan.joins.mapNotNull { it.condition } +
            (if (discardProjection) emptyList() else query.select.map { it.expression } + query.values.orEmpty().flatten()) +
            (if (discardOrdering) emptyList() else query.orderBy.map { it.expression })
    }

    private fun inputs(plan: BoundQuery, expressions: List<SqlExpr>): List<CatalogColumn> {
        val needed = linkedSetOf<CatalogColumn>()
        fun visit(expression: SqlExpr) {
            if (expression is SqlExpr.Column) plan.references[expression]?.let(needed::add)
            plan.subqueries[expression]?.externalColumns()?.filter { column -> column.sourceId in plan.sources.map { it.id } }?.let(needed::addAll)
            children(expression).forEach(::visit)
        }
        expressions.forEach(::visit)
        plan.sources.mapNotNull { it.unnest }.forEach(::visit)
        return needed.toList()
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

    private suspend fun exists(plan: BoundQuery, outerRow: InputRow = emptyMap()): Boolean {
        val correlated = plan.externalColumns().isNotEmpty()
        if (!correlated) existenceCache[plan]?.let { return it }
        var found = false
        produce(plan, QueryOutput.EXISTS, outerRow) { found = true; false }
        if (!correlated) existenceCache[plan] = found
        return found
    }

    private suspend fun produce(plan: BoundQuery, output: QueryOutput = QueryOutput.VALUES, outerRow: InputRow = emptyMap(), emit: suspend (List<SqlValue>) -> Boolean): Boolean {
        preflight(plan, output, outerRow)
        val query = plan.query
        val evaluator = evaluator(plan, outerRow)
        val limit = query.limit?.let { nonNegative(evaluator.eval(it), "LIMIT") } ?: Long.MAX_VALUE
        val offset = query.offset?.let { nonNegative(evaluator.eval(it), "OFFSET") } ?: 0L
        if (limit == 0L) return true
        val discardProjection = output != QueryOutput.VALUES && (!query.distinct || output == QueryOutput.EXISTS && offset == 0L)
        val childOutput = childOutput(output, discardProjection, offset)
        val distinct = query.distinct && !discardProjection
        val orderBy = if (output != QueryOutput.VALUES || nativeSqlOrder(plan) != null) emptyList() else query.orderBy
        val inputs = inputs(plan, expressions(plan, discardProjection, output != QueryOutput.VALUES))
        val emptyProjection = if (discardProjection) List(plan.columns.size) { SqlValue.Null } else emptyList()
        val memory = SqlMemory(budget)
        val windows = mutableListOf<SqlExpr.Window>()
        fun findWindows(expression: SqlExpr) {
            if (expression is SqlExpr.Window) windows += expression else children(expression).forEach(::findWindows)
        }
        (if (discardProjection) emptyList() else query.select.map { it.expression }).plus(orderBy.map { it.expression }).forEach(::findWindows)
        val aggregateCalls = streamingAggregateCalls(plan, orderBy, discardProjection)
        val aggregateGroups = linkedMapOf<List<Any>, Pair<InputRow, List<SqlAggregateState>>>()
        fun newGroup(row: InputRow): Pair<InputRow, List<SqlAggregateState>> {
            if (aggregateGroups.size >= sql.options.maxGroups) budget.exceeded("groups", sql.options.maxGroups.toLong(), aggregateGroups.size.toLong() + 1)
            memory.retain(128L + row.values.sumOf(::valueBytes))
            return row to aggregateCalls.orEmpty().map { SqlAggregateState(it, evaluator, budget) }
        }
        if (aggregateCalls != null && query.groupBy.isEmpty()) aggregateGroups[emptyList()] = newGroup(emptyMap())
        try {
            val retained = mutableListOf<InputRow>()
            val outputs = mutableListOf<OutputRow>()
            val blocking = plan.aggregate || orderBy.isNotEmpty() || distinct
            var passed = 0L
            var produced = 0L
            var downstream = true
            var sequence = 0L
            val topCount = if (orderBy.isNotEmpty() && !distinct && limit != Long.MAX_VALUE && offset <= Int.MAX_VALUE.toLong() - limit) (offset + limit).toInt() else null
            val top = topCount?.let { SqlTopRows(it, orderBy, budget, memory) }
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
                val order = orderBy.map { evaluator.eval(it.expression, row, group) }
                val outputRow = OutputRow(normalized, order, sequence++)
                if (top != null) top.add(outputRow) else {
                    memory.retain(outputRow.bytes)
                    outputs += outputRow
                }
                return true
            }
            when {
                query.values != null || plan.setOperands != null || plan.body != null -> {
                    suspend fun accept(values: List<SqlValue>): Boolean {
                        val normalized = values.zip(plan.columns).map { (value, column) -> coerceSqlValue(value, column.type) }
                        val row = plan.sources.single().columns.associateWith { normalized[it.outputIndex!!] }
                        return projected(normalized, row)
                    }
                    if (plan.body != null) {
                        produce(plan.body, childOutput, outerRow) { values -> accept(values) }
                    } else if (query.values != null) {
                        for (expressions in query.values) {
                            budget.step()
                            if (!accept(if (discardProjection) emptyProjection else expressions.map { evaluator.eval(it) })) break
                        }
                    } else {
                        val operands = plan.setOperands!!
                        val operation = query.setOperation!!
                        if (operation.operator == SqlSetOperator.UNION && operation.all) {
                            var keepReading = true
                            produce(operands.first, childOutput, outerRow) { values -> accept(values).also { keepReading = it } }
                            if (keepReading) produce(operands.second, childOutput, outerRow) { values -> accept(values) }
                        } else {
                            val leftResult = subqueryRows(operands.first, outerRow)
                            try {
                                val rightResult = subqueryRows(operands.second, outerRow)
                                try {
                                    val left = leftResult.values
                                    val right = rightResult.values
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
                                } finally { rightResult.close() }
                            } finally { leftResult.close() }
                        }
                    }
                }
                else -> {
                    suspend fun consume(row: InputRow): Boolean {
                        budget.step()
                        if (query.where != null && evaluator.eval(query.where, row).truth() != true) return true
                        if (windows.isNotEmpty()) {
                            memory.retain(128L + row.values.sumOf(::valueBytes)); retained += row
                            return true
                        }
                        if (plan.aggregate) {
                            if (aggregateCalls != null) {
                                val key = query.groupBy.map { evaluator.eval(it, row) }.map(::sqlKey)
                                val group = aggregateGroups.getOrPut(key) { newGroup(row) }
                                for (state in group.second) state.add(row)
                            } else {
                                memory.retain(128L + row.values.sumOf(::valueBytes))
                                retained += row
                            }
                            return true
                        }
                        return projected(if (discardProjection) emptyProjection else query.select.map { evaluator.eval(it.expression, row) }, row)
                    }
                    val constantFalse = query.where != null && !containsColumn(query.where) && !containsSubquery(query.where) && evaluator.eval(query.where).truth() != true
                    if (!constantFalse) {
                        if (plan.sources.isEmpty()) consume(emptyMap()) else joinRows(plan, inputs, evaluator, ::consume)
                    }
                    if (windows.isNotEmpty()) {
                        val results = calculateWindows(windows.distinct(), retained, evaluator, budget, memory)
                        for ((index, row) in retained.withIndex()) {
                            evaluator.windowResults = results.mapValues { it.value[index] }
                            if (!projected(if (discardProjection) emptyProjection else query.select.map { evaluator.eval(it.expression, row) }, row)) break
                        }
                        evaluator.windowResults = emptyMap()
                    } else if (plan.aggregate) {
                        suspend fun projectGroup(group: List<InputRow>) {
                            val row = group.firstOrNull().orEmpty()
                            if (query.having != null && evaluator.eval(query.having, row, group).truth() != true) return
                            projected(if (discardProjection) emptyProjection else query.select.map { evaluator.eval(it.expression, row, group) }, row, group)
                        }
                        if (aggregateCalls != null) {
                            for ((row, states) in aggregateGroups.values) {
                                evaluator.aggregateResults = aggregateCalls.zip(states.map { it.result() }).toMap()
                                if (query.having == null || evaluator.eval(query.having, row).truth() == true) {
                                    projected(if (discardProjection) emptyProjection else query.select.map { evaluator.eval(it.expression, row) }, row)
                                }
                            }
                            evaluator.aggregateResults = null
                        } else if (query.groupBy.isEmpty()) projectGroup(retained) else {
                            val groups = linkedMapOf<List<Any>, MutableList<InputRow>>()
                            for (row in retained) {
                                budget.step()
                                val values = query.groupBy.map { evaluator.eval(it, row) }
                                val key = values.map(::sqlKey)
                                val group = groups.getOrPut(key) {
                                    if (groups.size >= sql.options.maxGroups) budget.exceeded("groups", sql.options.maxGroups.toLong(), groups.size.toLong() + 1)
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
            var result: List<OutputRow> = top?.rows ?: outputs
            if (distinct) {
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
            if (orderBy.isNotEmpty()) result = mergeSort(result, orderBy, budget)
            for (row in result) if (!deliver(row.values)) break
            return downstream
        } finally {
            aggregateGroups.values.forEach { (_, states) -> states.forEach { it.close() } }
            memory.close()
        }
    }

    private suspend fun sourceRows(plan: BoundQuery, source: BoundSource, requiredInputs: List<CatalogColumn>, evaluator: SqlEvaluator, accessOverride: NativeAccess? = null, emit: suspend (InputRow) -> Boolean): Boolean {
        if (source.unnest != null) {
            for (row in collectionRows(source, evaluator.eval(source.unnest))) { budget.step(); if (!emit(row)) return false }
            return true
        }
        val relation = source.relation
        if (relation != null) {
            val result = subqueryRows(relation, evaluator.outerRow)
            try {
                for (values in result.values) {
                    budget.step()
                    if (!emit(source.columns.associateWith { values[it.outputIndex!!] })) return false
                }
                return true
            } finally { result.close() }
        }
        val table = source.table!!
        val inputs = requiredInputs.filter { it.sourceId == source.id }
        val rootIndices = inputs.mapNotNull { it.indices.firstOrNull() }.distinct()
        // Some stores derive metadata's effective lastVersion from the selected values. Fetch the
        // whole record whenever SQL needs __version, so an optimistic write guard never receives
        // a version from a partial projection.
        val select = if (inputs.any { it.isVersion }) null else table.model.graph { rootIndices.map { this[it]!! } }
        val access = accessOverride ?: nativeAccess(plan, source)
        val where = access.filter(table, evaluator)
        if (access.keyExpressions != null) {
            val keys = linkedSetOf<Key<IsRootDataModel>>()
            val memory = SqlMemory(budget)
            try {
                for (expression in access.keyExpressions) {
                    val value = evaluator.eval(expression)
                    if (value == SqlValue.Null) continue
                    val binding = source.columns.single { it.isKey && it.outputIndex == null }
                    val key = domainValue(value, binding, SqlErrorCode.TYPE) as SqlValue.Key
                    if (keys.add(Key(key.value.bytes))) memory.retain(64L + key.value.size)
                }
                for (batch in keys.toList().chunked(sql.options.pageSize)) {
                    budget.checkpoint()
                    snapshot()
                    budget.request()
                    val page = sql.dataStore.execute(table.model.get(*batch.toTypedArray(), select = select, where = where, toVersion = budget.snapshotVersion, filterSoftDeleted = readOptions.filterSoftDeleted))
                    budget.fetched(page.values.size)
                    budget.accessed("${table.schema}.${table.name}", SqlAccessPath.GET, null, page.values.size)
                    for (record in page.values) {
                        budget.step()
                        if (!emit(inputs.associateWith { budget.checkValue(it.read(record)) })) return false
                    }
                }
                return true
            } finally { memory.close() }
        }
        if (!sql.options.allowTableScan && where == null && nativeSqlOrder(plan)?.propertyReference == null) throw SqlException(SqlErrorCode.UNSUPPORTED, "This query requires a table scan; enable SqlOptions.allowTableScan with appropriate budgets")
        var cursor: ScanCursor? = null
        do {
            budget.checkpoint()
            snapshot()
            budget.request()
            val page = try {
                sql.dataStore.execute(table.model.scan(select = select, where = where, order = nativeSqlOrder(plan), limit = sql.options.pageSize.toUInt(), cursor = cursor,
                    toVersion = budget.snapshotVersion, filterSoftDeleted = readOptions.filterSoftDeleted, allowTableScan = sql.options.allowTableScan))
            } catch (error: StorageException) {
                if (!sql.options.allowTableScan && error.message?.contains("minimum key scan bytes") == true) throw SqlException(SqlErrorCode.UNSUPPORTED, "No fitting indexed/key access path; table scans are disabled", cause = error)
                throw error
            }
            budget.fetched(page.values.size)
            val fetch = page.dataFetchType
            budget.accessed("${table.schema}.${table.name}", when (fetch) {
                is FetchByIndexScan -> SqlAccessPath.INDEX_SCAN
                is FetchByTableScan -> SqlAccessPath.TABLE_SCAN
                else -> SqlAccessPath.UNKNOWN_SCAN
            }, (fetch as? FetchByIndexScan)?.index?.let { Bytes(it).toString() }, page.values.size)
            for (record in page.values) {
                budget.step()
                if (!emit(inputs.associateWith { budget.checkValue(it.read(record)) })) return false
            }
            cursor = page.nextCursor
        } while (cursor != null)
        return true
    }

    private suspend fun joinRows(plan: BoundQuery, inputs: List<CatalogColumn>, evaluator: SqlEvaluator, emit: suspend (InputRow) -> Boolean) {
        if (plan.joins.isEmpty()) { sourceRows(plan, plan.sources.first(), inputs, evaluator, emit = emit); return }
        if (lookupJoin(plan, inputs, evaluator, emit)) return
        val memory = SqlMemory(budget)
        val hashMemories = mutableListOf<SqlMemory>()
        try {
        val rightRows = mutableListOf<List<InputRow>>()
        val keys = mutableListOf<List<Pair<CatalogColumn, CatalogColumn>>>()
        val hashes = mutableListOf<Map<List<Any>, List<InputRow>>?>()
        for (join in plan.joins) {
            if (join.source.unnest != null) { rightRows += emptyList<InputRow>(); keys += emptyList<Pair<CatalogColumn, CatalogColumn>>(); hashes += null; continue }
            val rows = mutableListOf<InputRow>()
            sourceRows(plan, join.source, inputs, evaluator) { row ->
                memory.retain(128L + row.values.sumOf(::valueBytes))
                rows += row
                true
            }
            rightRows += rows
            val pairs = mutableListOf<Pair<CatalogColumn, CatalogColumn>>()
            fun equi(expression: SqlExpr) {
                if (expression is SqlExpr.Binary && expression.operator == "AND") {
                    equi(expression.left)
                    if (isSafeToReorder(expression.left)) equi(expression.right)
                    return
                }
                if (expression is SqlExpr.Binary && expression.operator == "=") {
                    val l = (expression.left as? SqlExpr.Column)?.let { plan.references[it] }
                    val r = (expression.right as? SqlExpr.Column)?.let { plan.references[it] }
                    if (l != null && r != null) {
                        if (l.sourceId == join.source.id && r.sourceId in plan.sources.map { it.id } && r.sourceId != join.source.id) pairs += r to l
                        if (r.sourceId == join.source.id && l.sourceId in plan.sources.map { it.id } && l.sourceId != join.source.id) pairs += l to r
                    }
                }
            }
            join.condition?.let(::equi)
            keys += pairs
            if (pairs.isEmpty()) hashes += null else {
                val hash = mutableMapOf<List<Any>, MutableList<InputRow>>()
                val hashMemory = SqlMemory(budget)
                hashMemories += hashMemory
                var fits = true
                for (row in rows) {
                    budget.step()
                    val values = pairs.map { row[it.second] ?: SqlValue.Null }
                    if (values.any { it == SqlValue.Null }) continue
                    val key = values.map(::sqlKey)
                    val bytes = 8L + if (key in hash) 0L else 64L + values.sumOf(::valueBytes)
                    if (bytes > sql.options.maxBufferedBytes - budget.retainedBytes) { fits = false; break }
                    hashMemory.retain(bytes)
                    hash.getOrPut(key) { mutableListOf() } += row
                }
                if (fits) hashes += hash else { hashMemory.close(); hashes += null }
            }
        }
        suspend fun expand(row: InputRow, index: Int): Boolean {
            budget.step()
            if (index == plan.joins.size) return emit(row)
            val join = plan.joins[index]
            val hash = hashes[index]
            val matches = if (join.source.unnest != null) collectionRows(join.source, evaluator.eval(join.source.unnest, row)) else if (hash == null) rightRows[index] else {
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
        sourceRows(plan, plan.sources.first(), inputs, evaluator) { expand(it, 0) }
        } finally { hashMemories.forEach { it.close() }; memory.close() }
    }

    /** A reference-to-key equijoin uses bounded batched Get requests instead of scanning its target. */
    private suspend fun lookupJoin(plan: BoundQuery, inputs: List<CatalogColumn>, evaluator: SqlEvaluator, emit: suspend (InputRow) -> Boolean): Boolean {
        val join = plan.joins.singleOrNull() ?: return false
        val condition = join.condition as? SqlExpr.Binary ?: return false
        if (condition.operator != "=" || join.source.table == null) return false
        val a = (condition.left as? SqlExpr.Column)?.let { plan.references[it] } ?: return false
        val b = (condition.right as? SqlExpr.Column)?.let { plan.references[it] } ?: return false
        val (left, right) = when {
            a.isKey && a.sourceId == join.source.id && b.sourceId != join.source.id -> b to a
            b.isKey && b.sourceId == join.source.id && a.sourceId != join.source.id -> a to b
            else -> return false
        }
        if (left.sourceId != plan.sources.first().id || left.column.type != SqlType.KEY || left.domain != right.domain) return false
        val batch = mutableListOf<InputRow>()
        val memory = SqlMemory(budget)
        var more = true
        suspend fun flush(): Boolean {
            val keyExpressions = batch.mapNotNull { row -> (row[left] as? SqlValue.Key)?.let { key ->
                SqlExpr.Literal(key.value.toString(), LiteralKind.STRING, 0)
            } }
            val found = mutableMapOf<Any, InputRow>()
            sourceRows(plan, join.source, inputs + right, evaluator, NativeAccess(keyExpressions, emptyList())) { row ->
                memory.retain(128L + row.values.sumOf(::valueBytes))
                found[sqlKey(row.getValue(right))] = row
                true
            }
            for (row in batch) {
                budget.step()
                val match = found[sqlKey(row[left] ?: SqlValue.Null)]
                if (match != null) { if (!emit(row + match)) return false }
                else if (join.type == SqlJoinType.LEFT && !emit(row + join.source.columns.associateWith { SqlValue.Null })) return false
            }
            batch.clear()
            memory.close()
            return true
        }
        try {
            sourceRows(plan, plan.sources.first(), inputs + left, evaluator) { row ->
                memory.retain(128L + row.values.sumOf(::valueBytes))
                batch += row
                if (batch.size < sql.options.pageSize) true else flush().also { more = it }
            }
            if (more && batch.isNotEmpty()) flush()
        } finally { memory.close() }
        return true
    }

}

internal class SqlMemory(private val budget: SqlBudget) {
    private var retained = 0L
    fun retain(bytes: Long) { budget.retain(bytes); retained += bytes }
    fun release(bytes: Long) { budget.release(bytes); retained -= bytes }
    fun close() { budget.release(retained); retained = 0 }
}

internal fun nonNegative(value: SqlValue, label: String): Long = exactLong(value, label).also {
    if (it < 0) throw SqlException(SqlErrorCode.TYPE, "$label cannot be negative")
}

private data class OutputRow(val values: List<SqlValue>, val order: List<SqlValue>, val sequence: Long = 0) {
    val bytes get() = 96L + values.sumOf(::valueBytes) + order.sumOf(::valueBytes)
}

/** A max heap retains only LIMIT + OFFSET rows; equal keys retain their original order. */
private class SqlTopRows(private val count: Int, private val orders: List<SqlOrder>, private val budget: SqlBudget, private val memory: SqlMemory) {
    val rows = mutableListOf<OutputRow>()
    private fun compare(a: OutputRow, b: OutputRow): Int = compareRows(a, b, orders).takeIf { it != 0 } ?: a.sequence.compareTo(b.sequence)
    suspend fun add(row: OutputRow) {
        if (count == 0) return
        if (rows.size < count) {
            memory.retain(row.bytes)
            rows += row
            var index = rows.lastIndex
            while (index > 0) {
                budget.step()
                val parent = (index - 1) / 2
                if (compare(rows[index], rows[parent]) <= 0) break
                val swap = rows[parent]; rows[parent] = rows[index]; rows[index] = swap
                index = parent
            }
        } else if (compare(row, rows[0]) < 0) {
            memory.release(rows[0].bytes)
            memory.retain(row.bytes)
            rows[0] = row
            var index = 0
            while (index * 2 + 1 < rows.size) {
                budget.step()
                var child = index * 2 + 1
                if (child + 1 < rows.size && compare(rows[child + 1], rows[child]) > 0) child++
                if (compare(rows[index], rows[child]) >= 0) break
                val swap = rows[index]; rows[index] = rows[child]; rows[child] = swap
                index = child
            }
        }
    }
}

/** Stream eager aggregate expressions; keep lazy branches and potentially hidden failures on the reference path. */
internal fun streamingAggregateCalls(plan: BoundQuery, orders: List<SqlOrder>, discardProjection: Boolean = false): List<SqlExpr.Call>? {
    if (!plan.aggregate) return null
    val expressions = (if (discardProjection) emptyList() else plan.query.select.map { it.expression }) +
        orders.map { it.expression } + listOfNotNull(plan.query.having)
    val calls = mutableListOf<SqlExpr.Call>()
    fun collect(expression: SqlExpr): Boolean {
        if (!containsAggregate(expression)) return true
        if (expression is SqlExpr.Call && expression.name.normalized in aggregateNames) {
            if (expression.arguments.any { it !is SqlExpr.Star && !isSafeToReorder(it) } || expression.filter?.let { !isSafeToReorder(it) } == true) return false
            // HAVING may suppress a projection, including an overflowing SUM/AVG.
            if (plan.query.having != null && expression.name.normalized !in setOf("count", "min", "max")) return false
            calls += expression
            return true
        }
        if (expression is SqlExpr.Case || expression is SqlExpr.Subquery || expression is SqlExpr.Call ||
            expression is SqlExpr.Binary && expression.operator in setOf("AND", "OR")) return false
        return children(expression).all(::collect)
    }
    if (!expressions.all(::collect)) return null
    return calls.distinct()
}

/** A cooperative stable merge sort avoids an uninterruptible platform sort on browser targets. */
private suspend fun mergeSort(rows: List<OutputRow>, orders: List<SqlOrder>, budget: SqlBudget): List<OutputRow> = sqlStableSort(rows, budget) { a, b -> compareRows(a, b, orders) }

internal suspend fun <T> sqlStableSort(rows: List<T>, budget: SqlBudget, compare: (T, T) -> Int): List<T> {
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
                        compare(source[left], source[right]) <= 0 -> source[left++]
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

private fun compareRows(left: OutputRow, right: OutputRow, orders: List<SqlOrder>): Int = compareOrderValues(left.order, right.order, orders).takeIf { it != 0 } ?: left.sequence.compareTo(right.sequence)

internal fun compareOrderValues(left: List<SqlValue>, right: List<SqlValue>, orders: List<SqlOrder>): Int {
    for (index in orders.indices) {
        val l = left[index]
        val r = right[index]
        val order = orders[index]
        val comparison = if (l == SqlValue.Null || r == SqlValue.Null) {
            val nullsFirst = order.nullsFirst ?: !order.ascending
            when { l == r -> 0; l == SqlValue.Null -> if (nullsFirst) -1 else 1; else -> if (nullsFirst) 1 else -1 }
        } else sqlCompare(l, r)!!.let { if (order.ascending) it else -it }
        if (comparison != 0) return comparison
    }
    return 0
}


/** Only byte-compatible, complete access paths can replace SQL ordering. */
internal fun nativeSqlOrder(plan: BoundQuery): Order? {
    if (plan.query.select.any { containsWindow(it.expression) } || plan.aggregate || plan.query.distinct || plan.joins.isNotEmpty() || plan.sources.size != 1 || plan.sources.single().table == null) return null
    val order = plan.query.orderBy.singleOrNull() ?: return null
    val column = (order.expression as? SqlExpr.Column)?.let { plan.references[it] } ?: return null
    if (nativeAccess(plan, plan.sources.single()).keyExpressions != null) return null
    if (plan.query.select.any { !isSafeToReorder(it.expression) } || plan.query.where?.let { !isSafeToReorder(it) } == true) return null
    if (column.isKey) return if (order.ascending) Order.ascending else Order.descending
    // SQL can use a sparse property index only when its WHERE excludes absent values.
    if ((column.wrapper?.definition as? NumberDefinition<*>)?.reversedStorage == true) return null
    if (column.column.type !in setOf(SqlType.BOOLEAN, SqlType.INT64, SqlType.UINT64, SqlType.DATE, SqlType.TIME, SqlType.TIMESTAMP, SqlType.KEY, SqlType.ENUM)) return null
    fun excludesNull(expression: SqlExpr): Boolean = when (expression) {
        is SqlExpr.Binary -> if (expression.operator == "AND") excludesNull(expression.left) || excludesNull(expression.right)
            else expression.operator in setOf("=", "<", "<=", ">", ">=") && listOf(expression.left, expression.right).any { it is SqlExpr.Column && plan.references[it] == column }
        is SqlExpr.Is -> expression.test == "NULL" && expression.negated && (expression.operand as? SqlExpr.Column)?.let { plan.references[it] == column } == true
        else -> false
    }
    if (plan.query.where?.let(::excludesNull) != true) return null
    val reference = column.reference ?: return null
    val indexable = reference as? IsIndexable ?: return null
    if (plan.sources.single().table!!.model.Meta.indexes.orEmpty().none { it.referenceStorageByteArray == indexable.referenceStorageByteArray }) return null
    return if (order.ascending) reference.ascending() else reference.descending()
}

package maryk.sql

/** Portable logical plan. Actual native paths and indexes are reported by execution summaries. */
data class SqlQueryPlan(val operators: List<SqlPlanOperator>, val weakCurrentReads: Boolean)

data class SqlPlanOperator(
    val operation: String,
    val table: String? = null,
    val columns: List<String> = emptyList(),
    val strategy: String? = null,
    val children: List<SqlPlanOperator> = emptyList(),
)

internal fun describeSqlPlan(plan: BoundQuery, weakCurrentReads: Boolean): SqlQueryPlan {
    val seen = mutableSetOf<BoundQuery>()
    fun describe(query: BoundQuery): List<SqlPlanOperator> {
        if (!seen.add(query)) return listOf(SqlPlanOperator("REUSE"))
        val nodes = mutableListOf<SqlPlanOperator>()
        query.sources.forEach { source ->
            val table = source.table
            nodes += when {
                source.unnest != null -> SqlPlanOperator("UNNEST", strategy = "bounded lateral collection expansion")
                table != null -> SqlPlanOperator(if (nativeAccess(query, source).keyExpressions != null) "GET" else "SCAN",
                    "${table.schema}.${table.name}", query.inputs.filter { it.sourceId == source.id }.map { it.column.name },
                    if (nativeSqlOrder(query) != null) "native compatible key/index order" else "native exact predicates with SQL residual")
                source.relation != null -> SqlPlanOperator("RELATION", children = describe(source.relation))
                else -> SqlPlanOperator("VALUES")
            }
        }
        if (query.query.where != null) nodes += SqlPlanOperator("FILTER", strategy = "SQL three-valued logic")
        if (query.joins.isNotEmpty()) nodes += SqlPlanOperator("JOIN", strategy = "batched reference-to-key lookup, lateral collection expansion or bounded local hash/nested loop")
        if (query.aggregate) nodes += SqlPlanOperator("AGGREGATE", strategy = if (streamingAggregateCalls(query, query.query.orderBy) != null) "incremental per-group state" else "bounded retained groups with lazy expression evaluation")
        if (query.query.select.any { containsWindow(it.expression) } || query.query.orderBy.any { containsWindow(it.expression) }) nodes += SqlPlanOperator("WINDOW", strategy = "bounded partition state")
        if (query.query.distinct) nodes += SqlPlanOperator("DISTINCT")
        if (query.query.orderBy.isNotEmpty() && nativeSqlOrder(query) == null) nodes += SqlPlanOperator(if (query.query.limit != null && !query.query.distinct) "TOP_N" else "SORT", strategy = "stable SQL order and null placement")
        query.subqueries.values.forEach { child -> nodes += SqlPlanOperator("SUBQUERY", strategy = if (child.externalColumns().isEmpty()) "cached uncorrelated relation" else "bounded correlated evaluation", children = describe(child)) }
        query.body?.let { nodes += SqlPlanOperator("QUERY", children = describe(it)) }
        query.setOperands?.let { (left, right) -> nodes += SqlPlanOperator(query.query.setOperation!!.operator.name, children = describe(left) + describe(right)) }
        nodes += SqlPlanOperator("PAGINATION", strategy = "SQL LIMIT/OFFSET, without truncation on budget exhaustion")
        return nodes
    }
    return SqlQueryPlan(describe(plan), weakCurrentReads)
}

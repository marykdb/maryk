package maryk.sql

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.sql.syntax.mapWindows
import maryk.sql.syntax.SqlFrameUnit
import maryk.sql.syntax.LiteralKind
import maryk.sql.syntax.SqlExpr
import maryk.sql.syntax.SqlName
import maryk.sql.syntax.SqlOrder
import maryk.sql.syntax.SqlQuery
import maryk.sql.syntax.SqlSelect
import maryk.sql.syntax.SqlJoinType
import maryk.sql.syntax.SqlTableSource

internal data class ExpressionInfo(val type: SqlType, val nullable: Boolean = true, val domain: CatalogColumn? = null)

internal class BoundSource(val id: Int, val qualifier: SqlName, val table: SqlTable?, val relation: BoundQuery?, val columns: List<CatalogColumn>, val schemaQualified: Boolean = false, val unnest: SqlExpr? = null, val withOrdinality: Boolean = false)
internal class BoundJoin(val source: BoundSource, val type: SqlJoinType, val condition: SqlExpr?)
internal class BindingState {
    var nodes = 0
    var sourceId = 0
    val parameterTypes = mutableMapOf<Int, SqlType>()
    val parameterDomains = mutableMapOf<Int, CatalogColumn>()
    val numericParameters = mutableSetOf<Int>()
    fun node() { if (++nodes > 256) throw SqlException(SqlErrorCode.LIMIT, "SQL plan exceeds 256 relation nodes") }
}

internal class BoundQuery(
    val query: SqlQuery,
    val table: SqlTable?,
    val columns: List<SqlColumn>,
    val parameters: List<SqlParameter>,
    val references: Map<SqlExpr.Column, CatalogColumn>,
    val literals: Map<SqlExpr.Literal, SqlValue>,
    val expressionTypes: Map<SqlExpr, ExpressionInfo>,
    val aggregate: Boolean,
    val sources: List<BoundSource>,
    val joins: List<BoundJoin>,
    val subqueries: Map<SqlExpr, BoundQuery>,
    val ctes: List<BoundQuery>,
    val setOperands: Pair<BoundQuery, BoundQuery>?,
    val outputBindings: List<CatalogColumn>,
    val bindingState: BindingState,
    val body: BoundQuery? = null,
) {
    val inputs: List<CatalogColumn> = references.values.distinct()
    val children: List<BoundQuery> = ctes + sources.mapNotNull { it.relation } + subqueries.values + listOfNotNull(setOperands?.first, setOperands?.second, body)
    val depth: Int = 1 + (children.maxOfOrNull { it.depth } ?: 0)
    init { if (depth > 64) throw SqlException(SqlErrorCode.LIMIT, "SQL plan exceeds 64 relation levels") }
}

internal val aggregateNames = setOf("count", "sum", "avg", "min", "max")

internal class SqlBinder(
    private val catalog: SqlCatalog,
    private val options: SqlOptions,
    private val state: BindingState = BindingState(),
    private val inheritedCtes: Map<String, BoundQuery> = emptyMap(),
    private val forbiddenCtes: Set<String> = emptySet(),
    private val outerScopes: List<List<BoundSource>> = emptyList(),
) {
    private var table: SqlTable? = null
    private val sources = mutableListOf<BoundSource>()
    private val joins = mutableListOf<BoundJoin>()
    private val ctes = inheritedCtes.toMutableMap()
    private val localCtes = mutableListOf<BoundQuery>()
    private val subqueries = mutableMapOf<SqlExpr, BoundQuery>()
    private val references = mutableMapOf<SqlExpr.Column, CatalogColumn>()
    private val literals = mutableMapOf<SqlExpr.Literal, SqlValue>()
    private val types = mutableMapOf<SqlExpr, ExpressionInfo>()
    private val parameterTypes get() = state.parameterTypes

    fun bind(input: SqlQuery): BoundQuery {
        state.node()
        val definitions = input.windows.associate { it.first.normalized to it.second }
        if (definitions.size != input.windows.size) fail("Duplicate window name")
        fun resolve(expression: SqlExpr): SqlExpr = expression.mapWindows { window ->
            val name = window.windowName
            if (name == null) window else {
                val spec = definitions[name.normalized] ?: fail("Unknown window '${name.text}'", window)
                window.copy(partitionBy = spec.partitionBy, orderBy = spec.orderBy, frame = spec.frame, windowName = null)
            }
        }
        val parsed = input.copy(select = input.select.map { it.copy(expression = resolve(it.expression)) },
            orderBy = input.orderBy.map { it.copy(expression = resolve(it.expression)) },
            where = input.where?.let(::resolve), having = input.having?.let(::resolve), groupBy = input.groupBy.map(::resolve))
        val localNames = parsed.ctes.map { it.name.normalized }
        if (localNames.toSet().size != localNames.size) fail("Duplicate CTE name")
        localNames.forEach { ctes.remove(it) }
        parsed.ctes.forEachIndexed { index, cte ->
            var relation = SqlBinder(catalog, options, state, ctes.toMap(), forbiddenCtes + localNames.drop(index), outerScopes).bind(cte.query)
            if (cte.columns.isNotEmpty()) {
                if (cte.columns.size != relation.columns.size) fail("CTE column list does not match query width")
                relation = renamed(relation, cte.columns)
            }
            uniqueOutputs(relation)
            ctes[cte.name.normalized] = relation
            localCtes += relation
        }
        if (parsed.values != null || parsed.setOperation != null || parsed.body != null) return bindCompound(parsed)
        parsed.from?.let { source ->
            sources += bindSource(source, false)
            source.joins.forEach { join ->
                val right = bindSource(join.right, join.type == SqlJoinType.LEFT)
                sources += right
                join.condition?.let { bindExpr(it, false, SqlType.BOOLEAN) }
                joins += BoundJoin(right, join.type, join.condition)
            }
            table = sources.singleOrNull()?.table
        }
        for (spec in definitions.values) bindExpr(SqlExpr.Window(SqlExpr.Call(SqlName("count"), listOf(SqlExpr.Star(null, 0)), false, null, 0), spec.partitionBy, spec.orderBy, spec.frame, 0), true)
        val selections = parsed.select.flatMap { selection ->
            val expression = selection.expression
            if (expression !is SqlExpr.Star) listOf(selection) else {
                if (selection.alias != null) fail("A wildcard cannot have an alias", expression)
                if (sources.isEmpty()) fail("A wildcard requires FROM", expression)
                val selectedSources = sources.filter { expression.qualifier == null || expression.qualifier.normalized == it.qualifier.normalized }
                if (selectedSources.isEmpty()) fail("Unknown wildcard qualifier", expression)
                selectedSources.flatMap { source -> source.columns.map {
                    if (it.column.type == SqlType.ANY && it.outputIndex == null) unsupported("Wildcard includes non-scalar column '${it.column.name}'; select scalar columns explicitly", expression)
                    SqlSelect(SqlExpr.Column(listOf(source.qualifier, SqlName(it.column.name, true)), expression.position), null)
                } }
            }
        }
        if (selections.isEmpty()) fail("SELECT must produce at least one column")
        val aliases = selections.filter { it.alias != null }.groupBy { it.alias!!.normalized }
        fun projectionReference(expression: SqlExpr, allowAlias: Boolean): SqlExpr {
            if (expression is SqlExpr.Literal && expression.kind == LiteralKind.NUMBER && expression.text.all { it in '0'..'9' }) {
                val ordinal = expression.text.toIntOrNull() ?: fail("Projection ordinal is too large", expression)
                return selections.getOrNull(ordinal - 1)?.expression ?: fail("Projection ordinal is out of range", expression)
            }
            if (allowAlias && expression is SqlExpr.Column && expression.path.size == 1) {
                aliases[expression.path.single().normalized]?.let {
                    if (it.size != 1) fail("Ambiguous output alias", expression)
                    return it.single().expression
                }
            }
            return expression
        }
        val groupBy = parsed.groupBy.map { projectionReference(it, false) }
        val orderBy = parsed.orderBy.map { it.copy(expression = projectionReference(it.expression, true)) }
        val query = parsed.copy(select = selections, groupBy = groupBy, orderBy = orderBy)
        query.where?.let { bindExpr(it, false, SqlType.BOOLEAN) }
        groupBy.forEach { bindExpr(it, false) }
        selections.forEach { bindExpr(it.expression, true) }
        query.having?.let { bindExpr(it, true, SqlType.BOOLEAN) }
        orderBy.forEach { bindExpr(it.expression, true) }
        for (expression in listOfNotNull(query.limit, query.offset)) {
            if (containsColumn(expression) || containsSubquery(expression)) fail("LIMIT/OFFSET must be constant expressions", expression)
            val type = bindExpr(expression, false, SqlType.DECIMAL).type
            if (!type.isNumeric && type != SqlType.ANY && type != SqlType.NULL) fail("LIMIT/OFFSET must be numeric", expression)
        }
        val aggregate = groupBy.isNotEmpty() || selections.any { containsAggregate(it.expression) } ||
            query.having != null || orderBy.any { containsAggregate(it.expression) }
        if (aggregate && (selections.any { containsWindow(it.expression) } || orderBy.any { containsWindow(it.expression) })) unsupported("Use a derived aggregate relation before applying window functions", selections.first().expression)
        if (query.having?.let(::containsWindow) == true) fail("Window functions are unavailable in HAVING", query.having)
        if (aggregate) {
            val keys = groupBy.map(::boundKey).toSet()
            selections.forEach { checkGrouped(it.expression, keys) }
            query.having?.let { checkGrouped(it, keys) }
            orderBy.forEach { checkGrouped(it.expression, keys) }
        }
        if (query.distinct) {
            val selected = selections.map { boundKey(it.expression) }.toSet()
            orderBy.forEach { if (boundKey(it.expression) !in selected) fail("ORDER BY expression must appear in SELECT DISTINCT", it.expression) }
        }
        val columns = selections.mapIndexed { index, selection ->
            val info = types.getValue(selection.expression)
            SqlColumn(selection.alias?.text ?: outputName(selection.expression, index), info.type, info.nullable)
        }
        return finish(query, columns, aggregate)
    }

    private fun finish(query: SqlQuery, columns: List<SqlColumn>, aggregate: Boolean, operands: Pair<BoundQuery, BoundQuery>? = null, outputDomains: List<CatalogColumn?>? = null, body: BoundQuery? = null): BoundQuery {
        val outputs = columns.mapIndexed { index, column ->
            val selection = query.select.getOrNull(index)
            val expression = selection?.expression
            val origin = outputDomains?.get(index) ?: (expression as? SqlExpr.Column)?.let { references[it] } ?: expression?.let { types[it]?.domain }
            val identifier = selection?.alias ?: (expression as? SqlExpr.Column)?.path?.last() ?: origin?.identifier ?: SqlName(column.name)
            (origin ?: CatalogColumn(column, emptyList(), null)).copy(column = column, sourceId = -1, outputIndex = index, identifier = identifier,
                parameterOrigins = expression?.let(::parameterOrigins) ?: origin?.parameterOrigins.orEmpty())
        }
        return BoundQuery(query, table, columns, List(query.parameterCount) { SqlParameter(it, parameterTypes[it] ?: SqlType.ANY) }, references.toMap(), literals.toMap(), types.toMap(), aggregate,
            sources.toList(), joins.toList(), subqueries.toMap(), localCtes.toList(), operands, outputs, state, body)
    }

    private fun renamed(plan: BoundQuery, names: List<SqlName>): BoundQuery {
        val columns = plan.columns.zip(names).map { (column, name) -> column.copy(name = name.text) }
        return BoundQuery(plan.query, plan.table, columns, plan.parameters, plan.references, plan.literals, plan.expressionTypes, plan.aggregate,
            plan.sources, plan.joins, plan.subqueries, plan.ctes, plan.setOperands, plan.outputBindings.mapIndexed { index, binding -> binding.copy(column = columns[index], identifier = names[index]) }, state, plan.body)
    }

    private fun uniqueOutputs(plan: BoundQuery) {
        if (plan.outputBindings.groupBy { it.identifier?.normalized ?: asciiLower(it.column.name) }.any { it.value.size > 1 }) fail("Duplicate names in relation output; supply distinct aliases")
    }

    private fun bindSource(source: SqlTableSource, nullable: Boolean): BoundSource {
        state.node()
        val qualifier = source.alias ?: source.name.lastOrNull() ?: fail("Derived table requires alias")
        if (sources.any { it.qualifier.normalized == qualifier.normalized }) fail("Duplicate source alias '${qualifier.text}'")
        if (source.unnest != null) {
            val expression = source.unnest
            val prototype = when (expression) {
                is SqlExpr.Column -> {
                    val column = resolveColumn(expression)
                    val columns = collectionColumns(column.wrapper?.definition) ?: column.collectionElementColumns
                        ?: if (column.column.type == SqlType.ANY && sources.any { it.id == column.sourceId && it.unnest != null }) listOf(CatalogColumn(SqlColumn("value", SqlType.ANY), emptyList(), null), CatalogColumn(SqlColumn("entry_key", SqlType.ANY), emptyList(), null)) else null
                    if (columns == null) unsupported("UNNEST requires a supported list, set or map", expression)
                    references[expression] = column
                    types[expression] = ExpressionInfo(SqlType.COLLECTION, column.column.nullable)
                    columns
                }
                is SqlExpr.Parameter -> { bindExpr(expression, false, SqlType.COLLECTION); listOf(CatalogColumn(SqlColumn("value", SqlType.ANY), emptyList(), null), CatalogColumn(SqlColumn("entry_key", SqlType.ANY), emptyList(), null)) }
                else -> unsupported("UNNEST accepts a model collection column or collection parameter", expression)
            }
            val id = state.sourceId++
            val columns = (prototype + if (source.withOrdinality) listOf(CatalogColumn(SqlColumn("ordinality", SqlType.INT64, false), emptyList(), null)) else emptyList()).mapIndexed { index, column -> column.copy(sourceId = id, outputIndex = index, column = column.column.copy(nullable = nullable || column.column.nullable)) }
            return BoundSource(id, qualifier, null, null, columns, unnest = expression, withOrdinality = source.withOrdinality)
        }
        val metadata = catalog.metadata(source)
        val relation = when {
            metadata != null -> SqlBinder(catalog, options, state).bind(metadata)
            source.derived != null -> SqlBinder(catalog, options, state, ctes.toMap(), forbiddenCtes, listOf(sources.toList()) + outerScopes).bind(source.derived)
            source.name.size == 1 && source.name.single().normalized in forbiddenCtes && source.name.single().normalized !in ctes -> fail("Forward or recursive CTE reference is not supported")
            source.name.size == 1 -> ctes[source.name.single().normalized]
            else -> null
        }
        val table = if (relation != null) null else {
            if (source.name.size !in 1..2) fail("Use table or schema.table names")
            val name = source.name.last()
            val schema = source.name.dropLast(1).singleOrNull()
            catalog.tables.singleOrNull { matches(name, it.name) && if (schema != null) matches(schema, it.schema) else asciiLower(it.schema) == asciiLower(options.defaultSchema) }
                ?: fail("Unknown table '${source.name.joinToString(".") { it.text }}'")
        }
        if (relation != null) {
            if (source.derived != null && relation.externalColumns().isNotEmpty()) fail("Correlated derived tables require a supported lateral source")
            uniqueOutputs(relation)
        }
        val id = state.sourceId++
        val columns = (relation?.outputBindings ?: table!!.bindings).map { it.copy(sourceId = id, column = it.column.copy(nullable = nullable || it.column.nullable)) }
        return BoundSource(id, qualifier, table, relation, columns, source.alias == null)
    }

    private fun bindCompound(parsed: SqlQuery): BoundQuery {
        val body = parsed.body?.let { SqlBinder(catalog, options, state, ctes.toMap(), forbiddenCtes, outerScopes).bind(it) }
        val operands = parsed.setOperation?.let {
            SqlBinder(catalog, options, state, ctes.toMap(), forbiddenCtes, outerScopes).bind(it.left) to
                SqlBinder(catalog, options, state, ctes.toMap(), forbiddenCtes, outerScopes).bind(it.right)
        }
        val domains: List<CatalogColumn?>
        val columns: List<SqlColumn>
        if (body != null) {
            domains = body.outputBindings
            columns = body.columns
        } else if (operands != null) {
            val (left, right) = operands
            if (left.columns.size != right.columns.size) fail("Set operands must have equal column counts")
            domains = left.outputBindings.zip(right.outputBindings).map { (l, r) ->
                if (l.domain != null && r.domain != null && l.domain != r.domain) fail("Set operands use different model/enum domains")
                (if (l.domain != null || r.domain == null) l else r).copy(identifier = l.identifier, parameterOrigins = l.parameterOrigins + r.parameterOrigins)
            }
            columns = left.columns.zip(right.columns).map { (l, r) -> SqlColumn(l.name, commonType(l.type, r.type), l.nullable || r.nullable) }
            columns.forEachIndexed { index, column ->
                val domain = domains[index]
                domain.parameterOrigins.forEach {
                    val parameter = SqlExpr.Parameter(it, 0)
                    inferParameter(it, column.type, parameter)
                    if (domain.domain != null) inferDomain(it, domain, parameter)
                }
            }
        } else {
            val values = parsed.values!!
            val width = values.first().size
            if (values.any { it.size != width }) fail("VALUES rows must have equal size")
            val infos = List(width) { index ->
                values.map { bindExpr(it[index], false) }.reduce { left, right ->
                    if (left.domain?.domain != null && right.domain?.domain != null && left.domain.domain != right.domain.domain) fail("VALUES use different model/enum domains")
                    ExpressionInfo(commonType(left.type, right.type), left.nullable || right.nullable, left.domain ?: right.domain)
                }
            }
            columns = infos.mapIndexed { index, info -> SqlColumn("column${index + 1}", info.type, info.nullable) }
            values.forEach { row -> row.forEachIndexed { index, expression ->
                constrainUnknown(expression, columns[index].type)
                infos[index].domain?.let { bindDomainOperand(expression, it) }
            } }
            domains = infos.mapIndexed { index, info ->
                (info.domain ?: CatalogColumn(columns[index], emptyList(), null)).copy(identifier = SqlName(columns[index].name), parameterOrigins = values.flatMap { parameterOrigins(it[index]) }.toSet())
            }
        }
        val id = state.sourceId++
        val outputSource = BoundSource(id, SqlName("__output"), null, null, columns.mapIndexed { index, column ->
            domains[index].copy(column = column, sourceId = id, outputIndex = index)
        })
        sources += outputSource
        val orderBy = parsed.orderBy.map { order ->
            val expression = order.expression
            val resolved = if (expression is SqlExpr.Literal && expression.kind == LiteralKind.NUMBER && expression.text.all { it.isDigit() }) {
                val ordinal = expression.text.toIntOrNull() ?: fail("Projection ordinal is too large", expression)
                val binding = outputSource.columns.getOrNull(ordinal - 1) ?: fail("Projection ordinal is out of range", expression)
                // An ordinal identifies a slot even if multiple output columns share its name.
                SqlExpr.Column(listOf(SqlName(binding.column.name, true)), expression.position).also {
                    references[it] = binding
                    types[it] = ExpressionInfo(binding.column.type, binding.column.nullable, binding.takeIf { it.domain != null })
                }
            } else expression
            if (operands != null && resolved !is SqlExpr.Column) fail("Compound ORDER BY requires an output column or ordinal", resolved)
            if (resolved !in types) bindExpr(resolved, false)
            order.copy(expression = resolved)
        }
        listOfNotNull(parsed.limit, parsed.offset).forEach {
            if (containsColumn(it) || containsSubquery(it)) fail("LIMIT/OFFSET must be constant expressions", it)
            bindExpr(it, false, SqlType.DECIMAL)
        }
        return finish(parsed.copy(orderBy = orderBy), columns, false, operands, domains, body)
    }

    private fun bindExpr(expression: SqlExpr, allowAggregate: Boolean, expected: SqlType? = null): ExpressionInfo {
        var info = when (expression) {
            is SqlExpr.Literal -> {
                val value = literals.getOrPut(expression) { literalValue(expression) }
                ExpressionInfo(value.type, value == SqlValue.Null)
            }
            is SqlExpr.Column -> {
                val column = resolveColumn(expression)
                if (column.column.type == SqlType.ANY && column.outputIndex == null) unsupported("Column '${column.column.name}' is not scalar", expression)
                references[expression] = column
                if (column.column.type == SqlType.ANY && expected != null && expected != SqlType.ANY && expected != SqlType.NULL) {
                    column.parameterOrigins.forEach { inferParameter(it, expected, expression) }
                }
                val inferred = column.parameterOrigins.mapNotNull { parameterTypes[it] }.distinct().singleOrNull()
                val domain = column.takeIf { it.domain != null } ?: column.parameterOrigins.firstNotNullOfOrNull { state.parameterDomains[it] }
                ExpressionInfo(if (column.column.type == SqlType.ANY) inferred ?: SqlType.ANY else column.column.type, column.column.nullable, domain)
            }
            is SqlExpr.Parameter -> {
                if (expected != null && expected != SqlType.NULL && expected != SqlType.ANY) {
                    inferParameter(expression.index, expected, expression)
                }
                ExpressionInfo(parameterTypes[expression.index] ?: SqlType.ANY, true, state.parameterDomains[expression.index])
            }
            is SqlExpr.Star -> fail("Wildcard is only valid as SELECT * or COUNT(*)", expression)
            is SqlExpr.Subquery, is SqlExpr.Exists, is SqlExpr.InQuery -> {
                val query = when (expression) { is SqlExpr.Subquery -> expression.query; is SqlExpr.Exists -> expression.query; is SqlExpr.InQuery -> expression.query }
                val bound = subqueries.getOrPut(expression) { SqlBinder(catalog, options, state, ctes.toMap(), forbiddenCtes, listOf(sources.toList()) + outerScopes).bind(query) }
                if (expression !is SqlExpr.Exists && bound.columns.size != 1) fail("Scalar and IN subqueries require exactly one output column", expression)
                when (expression) {
                    is SqlExpr.Exists -> ExpressionInfo(SqlType.BOOLEAN, false)
                    is SqlExpr.InQuery -> {
                        var input = bindExpr(expression.operand, allowAggregate)
                        val output = bound.outputBindings.single()
                        val domain = output.takeIf { it.domain != null }
                        if (domain != null) { bindDomainOperand(expression.operand, domain); input = types.getValue(expression.operand) }
                        if (expression.operand is SqlExpr.Parameter) input = bindExpr(expression.operand, allowAggregate, bound.columns.single().type)
                        if (bound.columns.single().type == SqlType.ANY) {
                            output.parameterOrigins.forEach { index ->
                                inferParameter(index, input.type, expression)
                                input.domain?.let { inferDomain(index, it, expression) }
                            }
                        }
                        commonType(input.type, bound.columns.single().type, expression)
                        if (input.domain != null && domain != null && input.domain.domain != domain.domain) fail("Cannot compare different model/enum domains", expression)
                        ExpressionInfo(SqlType.BOOLEAN)
                    }
                    else -> ExpressionInfo(bound.columns.single().type, true, bound.outputBindings.single().takeIf { it.domain != null })
                }
            }
            is SqlExpr.Window -> {
                if (!allowAggregate) fail("Window functions are valid only in SELECT and ORDER BY", expression)
                if (children(expression).any(::containsWindow)) fail("Window functions cannot be nested", expression)
                expression.partitionBy.forEach { bindExpr(it, false) }
                expression.orderBy.forEach { bindExpr(it.expression, false) }
                val offsets = listOfNotNull(expression.frame.start.offset, expression.frame.end.offset)
                for (offset in offsets) {
                    if (containsColumn(offset) || containsSubquery(offset) || containsAggregate(offset) || containsWindow(offset)) fail("Window frame offset must be constant", offset)
                    bindExpr(offset, false, SqlType.DECIMAL)
                }
                if (expression.frame.unit == SqlFrameUnit.RANGE && offsets.isNotEmpty()) {
                    if (expression.orderBy.size != 1 || !types.getValue(expression.orderBy.single().expression).type.isNumeric) fail("Offset RANGE frames require one numeric ORDER BY expression", expression)
                }
                val call = expression.call
                when (call.name.normalized) {
                    "row_number", "rank", "dense_rank" -> {
                        if (call.arguments.isNotEmpty() || call.distinct || call.filter != null) fail("Ranking functions take no arguments, DISTINCT or FILTER", expression)
                        ExpressionInfo(SqlType.INT64, false)
                    }
                    "ntile" -> {
                        if (call.arguments.size != 1 || call.distinct || call.filter != null) fail("NTILE takes one constant bucket count", expression)
                        val argument = call.arguments.single()
                        if (containsColumn(argument) || containsSubquery(argument)) fail("NTILE bucket count must be constant", argument)
                        bindExpr(argument, false, SqlType.DECIMAL)
                        ExpressionInfo(SqlType.INT64, false)
                    }
                    "first_value", "last_value", "nth_value" -> {
                        if (call.arguments.size != (if (call.name.normalized == "nth_value") 2 else 1) || call.distinct || call.filter != null) fail("Invalid value window arguments", expression)
                        val input = bindExpr(call.arguments.first(), false)
                        call.arguments.getOrNull(1)?.let { if (containsColumn(it) || containsSubquery(it)) fail("NTH_VALUE index must be constant", it); bindExpr(it, false, SqlType.DECIMAL) }
                        input.copy(nullable = true)
                    }
                    "lag", "lead" -> {
                        if (call.arguments.size !in 1..3 || call.distinct || call.filter != null) fail("LAG/LEAD take one to three scalar arguments", expression)
                        val input = bindExpr(call.arguments[0], false)
                        call.arguments.getOrNull(1)?.let { if (containsColumn(it) || containsSubquery(it)) fail("Window offset must be constant", it); bindExpr(it, false, SqlType.DECIMAL) }
                        val fallback = call.arguments.getOrNull(2)?.let {
                            input.domain?.let { domain -> bindDomainOperand(it, domain) }
                            bindExpr(it, false)
                        }
                        val output = fallback?.let { commonType(input.type, it.type, expression) } ?: input.type
                        constrainUnknown(call.arguments[0], output)
                        call.arguments.getOrNull(2)?.let { constrainUnknown(it, output) }
                        ExpressionInfo(output, input.nullable || fallback?.nullable != false, input.domain ?: fallback?.domain)
                    }
                    else -> if (call.name.normalized in aggregateNames) bindCall(call, true) else unsupported("Unknown window function '${call.name.text}'", expression)
                }
            }
            is SqlExpr.Unary -> {
                val operand = if (expression.operator == "NOT") bindExpr(expression.operand, allowAggregate, SqlType.BOOLEAN)
                    else bindNumeric(expression.operand, allowAggregate)
                ExpressionInfo(if (expression.operator == "NOT") SqlType.BOOLEAN else numericResultType(operand.type), operand.nullable)
            }
            is SqlExpr.Binary -> bindBinary(expression, allowAggregate)
            is SqlExpr.Is -> {
                bindExpr(expression.operand, allowAggregate, if (expression.test == "NULL") null else SqlType.BOOLEAN)
                ExpressionInfo(SqlType.BOOLEAN, false)
            }
            is SqlExpr.In -> {
                expression.values.forEach { bindComparison(expression.operand, it, allowAggregate) }
                ExpressionInfo(SqlType.BOOLEAN)
            }
            is SqlExpr.Between -> {
                bindComparison(expression.operand, expression.lower, allowAggregate)
                bindComparison(expression.operand, expression.upper, allowAggregate)
                ExpressionInfo(SqlType.BOOLEAN)
            }
            is SqlExpr.Like -> {
                listOfNotNull(expression.operand, expression.pattern, expression.escape).forEach { bindExpr(it, allowAggregate, SqlType.TEXT) }
                ExpressionInfo(SqlType.BOOLEAN)
            }
            is SqlExpr.Call -> bindCall(expression, allowAggregate)
            is SqlExpr.Cast -> {
                bindExpr(expression.operand, allowAggregate)
                val type = castType(expression.type.name, expression)
                val precision = expression.type.precision
                val scale = expression.type.scale
                if (type == SqlType.DECIMAL) {
                    if (precision != null && precision !in 1..1024 || scale != null && (scale !in 0..18 || precision != null && scale > precision)) {
                        fail("DECIMAL precision must be 1..1024 and scale 0..18, no greater than precision", expression)
                    }
                } else if (precision != null || scale != null) unsupported("Type sizes are only supported for DECIMAL", expression)
                ExpressionInfo(type, types.getValue(expression.operand).nullable)
            }
            is SqlExpr.Case -> {
                expression.operand?.let { bindExpr(it, allowAggregate) }
                var result = SqlType.NULL
                var nullable = expression.otherwise == null
                var domain: CatalogColumn? = null
                expression.branches.forEach { (condition, value) ->
                    if (expression.operand == null) bindExpr(condition, allowAggregate, SqlType.BOOLEAN)
                    else bindComparison(expression.operand, condition, allowAggregate)
                    val branch = bindExpr(value, allowAggregate)
                    result = commonType(result, branch.type, expression)
                    if (domain != null && branch.domain != null && domain.domain != branch.domain.domain) fail("CASE branches use different model/enum domains", expression)
                    domain = domain ?: branch.domain
                    nullable = nullable || branch.nullable
                }
                expression.otherwise?.let {
                    val branch = bindExpr(it, allowAggregate)
                    result = commonType(result, branch.type, expression)
                    if (domain != null && branch.domain != null && domain.domain != branch.domain.domain) fail("CASE branches use different model/enum domains", expression)
                    domain = domain ?: branch.domain
                    nullable = nullable || branch.nullable
                }
                (expression.branches.map { it.second } + listOfNotNull(expression.otherwise)).forEach {
                    constrainUnknown(it, result)
                    domain?.let { domain -> bindDomainOperand(it, domain) }
                }
                ExpressionInfo(result, nullable, domain)
            }
        }
        if (expected != null && expected != SqlType.ANY && expected != SqlType.NULL && info.type == SqlType.ANY) {
            parameterOrigins(expression).forEach { inferParameter(it, expected, expression) }
            info = info.copy(type = expected)
        }
        if (expected != null && expected != SqlType.ANY && info.type != SqlType.NULL && info.type != SqlType.ANY && info.type != expected && !(expected.isNumeric && info.type.isNumeric)) {
            fail("Expected $expected, found ${info.type}", expression)
        }
        types[expression] = info
        return info
    }

    private fun bindBinary(expression: SqlExpr.Binary, allowAggregate: Boolean): ExpressionInfo {
        val operator = expression.operator
        if (operator in setOf("AND", "OR")) {
            bindExpr(expression.left, allowAggregate, SqlType.BOOLEAN)
            bindExpr(expression.right, allowAggregate, SqlType.BOOLEAN)
            return ExpressionInfo(SqlType.BOOLEAN)
        }
        if (operator == "||") {
            val left = bindExpr(expression.left, allowAggregate, SqlType.TEXT)
            val right = bindExpr(expression.right, allowAggregate, SqlType.TEXT)
            return ExpressionInfo(SqlType.TEXT, left.nullable || right.nullable)
        }
        if (operator in setOf("+", "-", "*", "/", "%")) {
            val left = bindNumeric(expression.left, allowAggregate)
            val right = bindNumeric(expression.right, allowAggregate)
            val type = commonType(left.type, right.type, expression)
            constrainNumeric(expression.left, type)
            constrainNumeric(expression.right, type)
            return ExpressionInfo(numericResultType(type), left.nullable || right.nullable)
        }
        bindComparison(expression.left, expression.right, allowAggregate)
        return ExpressionInfo(SqlType.BOOLEAN, !operator.contains("DISTINCT"))
    }

    private fun bindComparison(left: SqlExpr, right: SqlExpr, allowAggregate: Boolean) {
        var l = bindExpr(left, allowAggregate)
        var r = bindExpr(right, allowAggregate)
        if (l.domain != null) { bindDomainOperand(right, l.domain); r = types.getValue(right) }
        if (r.domain != null) { bindDomainOperand(left, r.domain); l = types.getValue(left) }
        if (left is SqlExpr.Parameter) l = bindExpr(left, allowAggregate, r.type)
        if (right is SqlExpr.Parameter) r = bindExpr(right, allowAggregate, l.type)
        if (l.type == SqlType.ANY && left is SqlExpr.Column) l = bindExpr(left, allowAggregate, r.type)
        if (r.type == SqlType.ANY && right is SqlExpr.Column) r = bindExpr(right, allowAggregate, l.type)
        if (l.type == SqlType.ANY) constrainUnknown(left, r.type)
        if (r.type == SqlType.ANY) constrainUnknown(right, l.type)
        if (l.type != SqlType.NULL && r.type != SqlType.NULL && l.type != SqlType.ANY && r.type != SqlType.ANY) commonType(l.type, r.type, left)
        val leftDomain = l.domain?.domain
        val rightDomain = r.domain?.domain
        if (leftDomain != null && rightDomain != null && leftDomain != rightDomain) fail("Cannot compare different model/enum domains", left)
    }

    private fun bindDomainOperand(expression: SqlExpr, domain: CatalogColumn) {
        fun constrain(indices: Set<Int>) {
            for (index in indices) {
                inferDomain(index, domain, expression)
            }
            if (indices.isNotEmpty()) types[expression] = ExpressionInfo(domain.column.type, true, domain)
        }
        when (expression) {
            is SqlExpr.Literal -> if (expression.kind == LiteralKind.STRING) {
                literals[expression] = domainValue(literals.getValue(expression), domain, SqlErrorCode.BINDING)
                types[expression] = ExpressionInfo(domain.column.type, false, domain)
            }
            is SqlExpr.Parameter -> constrain(setOf(expression.index))
            is SqlExpr.Column -> if (references[expression]?.column?.type == SqlType.ANY) constrain(parameterOrigins(expression))
            is SqlExpr.Subquery -> if (subqueries[expression]?.columns?.singleOrNull()?.type == SqlType.ANY) constrain(parameterOrigins(expression))
            else -> if (types[expression]?.type == SqlType.ANY) constrain(parameterOrigins(expression))
        }
    }

    private fun inferDomain(index: Int, domain: CatalogColumn, expression: SqlExpr) {
        val old = state.parameterDomains[index]
        if (old != null && old.domain != domain.domain) fail("Parameter is used with different model/enum domains", expression)
        inferParameter(index, domain.column.type, expression)
        state.parameterDomains[index] = domain
    }

    private fun inferParameter(index: Int, expected: SqlType, expression: SqlExpr) {
        if (expected == SqlType.ANY || expected == SqlType.NULL) return
        if (index in state.numericParameters && !expected.isNumeric) fail("Numeric parameter has a nonnumeric constraint", expression)
        val target = if (expected.isNumeric && expected != SqlType.FLOAT64) SqlType.DECIMAL else expected
        val old = parameterTypes[index]
        if (old != null && old != target) fail("Conflicting parameter types", expression)
        parameterTypes[index] = target
    }

    private fun constrainUnknown(expression: SqlExpr, type: SqlType) {
        if (types[expression]?.type == SqlType.ANY) parameterOrigins(expression).forEach { inferParameter(it, type, expression) }
    }

    private fun bindNumeric(expression: SqlExpr, allowAggregate: Boolean): ExpressionInfo {
        val info = bindExpr(expression, allowAggregate)
        if (!info.type.isNumeric && info.type != SqlType.ANY && info.type != SqlType.NULL) fail("Expected a numeric value", expression)
        if (info.type == SqlType.ANY) {
            val origins = parameterOrigins(expression)
            if (origins.any { parameterTypes[it]?.isNumeric == false }) fail("Expected a numeric parameter", expression)
            state.numericParameters += origins
        }
        return info
    }

    private fun constrainNumeric(expression: SqlExpr, type: SqlType) {
        constrainUnknown(expression, type)
        if (types[expression]?.type != SqlType.ANY) return
        when (expression) {
            is SqlExpr.Unary -> constrainNumeric(expression.operand, type)
            is SqlExpr.Binary -> if (expression.operator in setOf("+", "-", "*", "/", "%")) {
                constrainNumeric(expression.left, type)
                constrainNumeric(expression.right, type)
            }
            is SqlExpr.Call -> if (expression.name.normalized in setOf("abs", "floor", "ceil", "ceiling", "round", "sum", "avg", "min", "max")) constrainNumeric(expression.arguments.first(), type)
            else -> Unit
        }
    }

    private fun numericResultType(type: SqlType): SqlType = when (type) {
        SqlType.ANY, SqlType.FLOAT64 -> type
        else -> SqlType.DECIMAL
    }

    private fun parameterOrigins(expression: SqlExpr): Set<Int> = when (expression) {
        is SqlExpr.Parameter -> setOf(expression.index)
        is SqlExpr.Column -> references[expression]?.parameterOrigins.orEmpty()
        is SqlExpr.Subquery -> subqueries[expression]?.outputBindings?.singleOrNull()?.parameterOrigins.orEmpty()
        is SqlExpr.Unary -> if (expression.operator in setOf("+", "-")) parameterOrigins(expression.operand) else emptySet()
        is SqlExpr.Binary -> if (expression.operator in setOf("+", "-", "*", "/", "%")) parameterOrigins(expression.left) + parameterOrigins(expression.right) else emptySet()
        is SqlExpr.Case -> (expression.branches.flatMap { parameterOrigins(it.second) } + expression.otherwise?.let(::parameterOrigins).orEmpty()).toSet()
        is SqlExpr.Call -> when (expression.name.normalized) {
            "coalesce", "nullif", "sum", "avg", "min", "max" -> expression.arguments.flatMap(::parameterOrigins).toSet()
            "abs", "floor", "ceil", "ceiling", "round" -> parameterOrigins(expression.arguments.first())
            else -> emptySet()
        }
        else -> emptySet()
    }

    private fun bindCall(call: SqlExpr.Call, allowAggregate: Boolean): ExpressionInfo {
        val name = call.name.normalized
        if (name in aggregateNames) {
            if (!allowAggregate) fail("Aggregate '$name' is not valid here (or is nested)", call)
            if (call.arguments.size != 1) fail("$name expects one argument", call)
            val argument = call.arguments.single()
            val input = if (argument is SqlExpr.Star) {
                if (name != "count" || call.distinct || argument.qualifier != null) fail("Only COUNT(*) accepts a wildcard", call)
                ExpressionInfo(SqlType.ANY)
            } else if (name in setOf("sum", "avg")) bindNumeric(argument, false) else bindExpr(argument, false)
            call.filter?.let { bindExpr(it, false, SqlType.BOOLEAN) }
            return ExpressionInfo(when (name) { "count" -> SqlType.INT64; "sum", "avg" -> numericResultType(input.type); else -> input.type }, name != "count", input.domain.takeIf { name == "min" || name == "max" })
        }
        if (call.distinct || call.filter != null) fail("DISTINCT/FILTER require an aggregate", call)
        val arity = when (name) {
            "coalesce", "concat" -> 1..1024
            "nullif", "date_part" -> 2..2
            "substring", "substr" -> 2..3
            "round" -> 1..2
            "abs", "floor", "ceil", "ceiling", "lower", "upper", "length", "char_length", "trim", "ltrim", "rtrim" -> 1..1
            else -> unsupported("Unknown SQL function '${call.name.text}'", call)
        }
        if (call.arguments.size !in arity) fail("Wrong number of arguments for $name", call)
        val inputs = call.arguments.mapIndexed { index, argument ->
            if (index == 0 && name in setOf("abs", "floor", "ceil", "ceiling", "round")) return@mapIndexed bindNumeric(argument, allowAggregate)
            val expected = when (name) {
                "abs", "floor", "ceil", "ceiling", "round" -> SqlType.DECIMAL
                "lower", "upper", "length", "char_length", "trim", "ltrim", "rtrim", "concat" -> SqlType.TEXT
                "substring", "substr" -> if (index == 0) SqlType.TEXT else SqlType.DECIMAL
                "date_part" -> if (index == 0) SqlType.TEXT else null
                else -> null
            }
            bindExpr(argument, allowAggregate, expected)
        }
        return when (name) {
            "coalesce", "nullif" -> {
                val domains = inputs.mapNotNull { it.domain }
                if (domains.map { it.domain }.distinct().size > 1) fail("Function arguments use different model/enum domains", call)
                val type = inputs.fold(SqlType.NULL) { type, input -> commonType(type, input.type, call) }
                call.arguments.forEach {
                    constrainUnknown(it, type)
                    domains.firstOrNull()?.let { domain -> bindDomainOperand(it, domain) }
                }
                ExpressionInfo(type, name == "nullif" || inputs.all { it.nullable }, domains.firstOrNull())
            }
            "length", "char_length", "date_part" -> ExpressionInfo(SqlType.INT64, inputs.any { it.nullable })
            "abs", "floor", "ceil", "ceiling", "round" -> ExpressionInfo(numericResultType(inputs.first().type), inputs.any { it.nullable })
            else -> ExpressionInfo(SqlType.TEXT, inputs.any { it.nullable })
        }
    }

    private fun resolveColumn(expression: SqlExpr.Column): CatalogColumn {
        fun candidates(available: List<BoundSource>): List<CatalogColumn> {
            val path = expression.path
            val qualified = available.filter { path.size > 1 && path.first().normalized == it.qualifier.normalized }
            val schemas = available.filter { source ->
                val table = source.table
                path.size > 2 && source.schemaQualified && table != null && matches(path[0], table.schema) && matches(path[1], table.name)
            }
            val sources = qualified.ifEmpty { schemas.ifEmpty { available } }
            val remaining = path.drop(if (qualified.isNotEmpty()) 1 else if (schemas.isNotEmpty()) 2 else 0)
            return sources.flatMap { it.columns }.filter { binding ->
                if (remaining.size == 1) binding.identifier?.let { remaining.single().normalized == it.normalized } ?: matches(remaining.single(), binding.column.name)
                else binding.column.name.split('.').let { parts -> parts.size == remaining.size && parts.zip(remaining).all { (part, name) -> matches(name, part) } }
            }
        }
        val matches = candidates(sources)
        if (matches.size > 1) fail("Ambiguous column '${expression.path.joinToString(".") { it.text }}'", expression)
        if (matches.isNotEmpty()) return matches.single()
        fun shadows(available: List<BoundSource>): Boolean = expression.path.size > 1 && available.any { source ->
            expression.path.first().normalized == source.qualifier.normalized || source.schemaQualified && source.table?.let { table ->
                expression.path.size > 2 && matches(expression.path[0], table.schema) && matches(expression.path[1], table.name)
            } == true
        }
        if (shadows(sources)) fail("Unknown column in local source", expression)
        for (scope in outerScopes) {
            val outer = candidates(scope)
            if (outer.size > 1) fail("Ambiguous outer column", expression)
            if (outer.isNotEmpty()) return outer.single()
            if (shadows(scope)) fail("Unknown column in nearest outer source", expression)
        }
        fail("Unknown column '${expression.path.joinToString(".") { it.text }}'", expression)
    }

    private fun checkGrouped(expression: SqlExpr, keys: Set<String>) {
        if (boundKey(expression) in keys || expression is SqlExpr.Call && expression.name.normalized in aggregateNames) return
        if (expression is SqlExpr.Column && references[expression]?.sourceId !in sources.map { it.id }) return
        if (expression is SqlExpr.Column) fail("Column must appear in GROUP BY or an aggregate", expression)
        children(expression).forEach { checkGrouped(it, keys) }
    }

    private fun boundKey(expression: SqlExpr): String = if (expression is SqlExpr.Column) {
        references[expression]?.let { "column:${it.sourceId}:${it.column.name}" } ?: expressionKey(expression)
    } else expressionHead(expression) + children(expression).joinToString(prefix = "[", postfix = "]") { boundKey(it).let { key -> "${key.length}:$key" } }

    private fun commonType(left: SqlType, right: SqlType, expression: SqlExpr? = null): SqlType = when {
        left == SqlType.NULL -> right
        right == SqlType.NULL -> left
        left == SqlType.ANY && right.isNumeric && right != SqlType.FLOAT64 || right == SqlType.ANY && left.isNumeric && left != SqlType.FLOAT64 -> SqlType.DECIMAL
        left == SqlType.ANY -> right
        right == SqlType.ANY || left == right -> left
        left.isNumeric && right.isNumeric && left != SqlType.FLOAT64 && right != SqlType.FLOAT64 -> SqlType.DECIMAL
        else -> fail("Incompatible types $left and $right; use an explicit CAST", expression)
    }

    private fun outputName(expression: SqlExpr, index: Int): String = when (expression) {
        is SqlExpr.Column -> expression.path.last().text
        is SqlExpr.Call -> expression.name.text
        else -> "column${index + 1}"
    }

    private fun fail(message: String, expression: SqlExpr? = null): Nothing = throw SqlException(SqlErrorCode.BINDING, message, expression?.position)
    private fun unsupported(message: String, expression: SqlExpr): Nothing = throw SqlException(SqlErrorCode.UNSUPPORTED, message, expression.position)
}

internal fun matches(name: SqlName, candidate: String): Boolean = if (name.quoted) name.text == candidate else name.normalized == asciiLower(candidate)

internal fun literalValue(literal: SqlExpr.Literal): SqlValue = try {
    when (literal.kind) {
        LiteralKind.NULL -> SqlValue.Null
        LiteralKind.BOOLEAN -> SqlValue.Bool(literal.text.equals("true", true))
        LiteralKind.NUMBER -> parseSqlNumber(literal.text)
        LiteralKind.STRING -> SqlValue.Text(literal.text)
        LiteralKind.DATE -> SqlValue.Date(LocalDate.parse(literal.text))
        LiteralKind.TIME -> SqlValue.Time(LocalTime.parse(literal.text))
        LiteralKind.TIMESTAMP -> SqlValue.Timestamp(LocalDateTime.parse(literal.text.replace(' ', 'T')))
    }
} catch (error: IllegalArgumentException) {
    if (error is SqlException) throw error
    throw SqlException(SqlErrorCode.TYPE, "Invalid ${literal.kind} literal", literal.position, error)
}

internal fun castType(name: String, expression: SqlExpr): SqlType = when (asciiLower(name)) {
    "boolean", "bool" -> SqlType.BOOLEAN
    "integer", "int", "bigint", "int64" -> SqlType.INT64
    "uint64", "ubigint" -> SqlType.UINT64
    "decimal", "numeric" -> SqlType.DECIMAL
    "double", "float", "real", "float64" -> SqlType.FLOAT64
    "text", "varchar", "string" -> SqlType.TEXT
    "binary", "bytes", "blob" -> SqlType.BINARY
    "date" -> SqlType.DATE
    "time" -> SqlType.TIME
    "timestamp", "datetime" -> SqlType.TIMESTAMP
    else -> throw SqlException(SqlErrorCode.UNSUPPORTED, "Unsupported CAST type '$name'", expression.position)
}

internal fun children(expression: SqlExpr): List<SqlExpr> = when (expression) {
    is SqlExpr.Unary -> listOf(expression.operand)
    is SqlExpr.Binary -> listOf(expression.left, expression.right)
    is SqlExpr.Is -> listOf(expression.operand)
    is SqlExpr.In -> listOf(expression.operand) + expression.values
    is SqlExpr.Between -> listOf(expression.operand, expression.lower, expression.upper)
    is SqlExpr.Like -> listOfNotNull(expression.operand, expression.pattern, expression.escape)
    is SqlExpr.Window -> expression.call.arguments + listOfNotNull(expression.call.filter) + expression.partitionBy + expression.orderBy.map { it.expression } + listOfNotNull(expression.frame.start.offset, expression.frame.end.offset)
    is SqlExpr.Call -> expression.arguments + listOfNotNull(expression.filter)
    is SqlExpr.Cast -> listOf(expression.operand)
    is SqlExpr.Case -> listOfNotNull(expression.operand, expression.otherwise) + expression.branches.flatMap { listOf(it.first, it.second) }
    is SqlExpr.InQuery -> listOf(expression.operand)
    else -> emptyList()
}

internal fun containsAggregate(expression: SqlExpr): Boolean = if (expression is SqlExpr.Window) false else expression is SqlExpr.Call && expression.name.normalized in aggregateNames || children(expression).any(::containsAggregate)
internal fun containsWindow(expression: SqlExpr): Boolean = expression is SqlExpr.Window || children(expression).any(::containsWindow)
internal fun containsColumn(expression: SqlExpr): Boolean = expression is SqlExpr.Column || children(expression).any(::containsColumn)
internal fun containsSubquery(expression: SqlExpr): Boolean = expression is SqlExpr.Subquery || expression is SqlExpr.Exists || expression is SqlExpr.InQuery || children(expression).any(::containsSubquery)

/** Structural identity ignores source offsets but preserves quoted identifier semantics. */
internal fun expressionKey(expression: SqlExpr): String {
    return expressionHead(expression) + children(expression).joinToString(prefix = "[", postfix = "]") { child -> expressionKey(child).let { "${it.length}:$it" } }
}

private fun expressionHead(expression: SqlExpr): String = when (expression) {
        is SqlExpr.Literal -> "literal:${expression.kind}:${expression.text.length}:${expression.text}"
        is SqlExpr.Column -> "column:" + expression.path.joinToString(".") { it.normalized }
        is SqlExpr.Parameter -> "parameter:${expression.index}"
        is SqlExpr.Star -> "star:${expression.qualifier?.normalized}"
        is SqlExpr.Unary -> "unary:${expression.operator}"
        is SqlExpr.Binary -> "binary:${expression.operator}"
        is SqlExpr.Is -> "is:${expression.test}:${expression.negated}"
        is SqlExpr.In -> "in:${expression.negated}"
        is SqlExpr.Between -> "between:${expression.negated}"
        is SqlExpr.Like -> "like:${expression.negated}"
        is SqlExpr.Window -> "window:${expression.call.name.normalized}:${expression.call.distinct}:${expression.call.arguments.size}:${expression.partitionBy.size}:${expression.frame}:${expression.orderBy.map { it.ascending to it.nullsFirst }}"
        is SqlExpr.Call -> "call:${expression.name.normalized}:${expression.distinct}"
        is SqlExpr.Cast -> "cast:${expression.type}"
        is SqlExpr.Case -> "case:${expression.operand != null}:${expression.otherwise != null}"
        is SqlExpr.Subquery -> "subquery:${expression.query}"
        is SqlExpr.Exists -> "exists:${expression.negated}:${expression.query}"
        is SqlExpr.InQuery -> "inquery:${expression.negated}:${expression.query}"
}


internal fun BoundQuery.externalColumns(): Set<CatalogColumn> {
    val visited = mutableSetOf<BoundQuery>()
    val columns = mutableSetOf<CatalogColumn>()
    val ownSources = mutableSetOf<Int>()
    fun visit(plan: BoundQuery) {
        if (!visited.add(plan)) return
        ownSources += plan.sources.map { it.id }
        columns += plan.inputs
        plan.children.forEach(::visit)
    }
    visit(this)
    return columns.filterTo(mutableSetOf()) { it.sourceId !in ownSources }
}

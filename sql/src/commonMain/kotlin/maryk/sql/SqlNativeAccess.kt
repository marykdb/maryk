package maryk.sql

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.pairs.with
import maryk.sql.syntax.SqlExpr
import maryk.lib.exceptions.ParseException

/** Domain checking is comparison conversion, never model write validation. */
internal fun domainValue(value: SqlValue, column: CatalogColumn, code: SqlErrorCode): SqlValue {
    if (value == SqlValue.Null) return value
    try {
        return when (column.column.type) {
            SqlType.KEY -> {
                val key = when (value) {
                    is SqlValue.Text -> SqlValue.Key(column.domain!!, Bytes(value.value))
                    is SqlValue.Key -> value
                    else -> throw SqlException(code, "Expected a typed key or key text")
                }
                if (key.model != column.domain) throw SqlException(code, "Key belongs to a different model")
                val model = column.keyModel ?: throw SqlException(code, "Key model definition is unavailable")
                if (key.value.size != model.Meta.keyByteSize) throw SqlException(code, "Key byte length does not match model '${model.Meta.name}'")
                key
            }
            SqlType.ENUM -> {
                val definition = column.wrapper?.definition as? EnumDefinition<*> ?: throw SqlException(code, "Enum definition is unavailable")
                when (value) {
                    is SqlValue.Text -> definition.enum.resolve(value.value)?.let { SqlValue.Enum(column.domain!!, it.index, it.name) }
                        ?: throw SqlException(code, "Unknown enum value '${value.value}'")
                    is SqlValue.Enum -> {
                        if (value.domain != column.domain) throw SqlException(code, "Enum belongs to a different definition")
                        val canonical = definition.enum.resolve(value.name)
                        if (canonical == null || canonical.index != value.index) throw SqlException(code, "Enum name and index do not match its definition")
                        value
                    }
                    else -> throw SqlException(code, "Expected a typed enum or enum text")
                }
            }
            else -> value
        }
    } catch (error: IllegalArgumentException) {
        if (error is SqlException) throw error
        throw SqlException(code, "Invalid ${column.column.type} comparison value", cause = error)
    } catch (error: ParseException) {
        throw SqlException(code, "Invalid ${column.column.type} comparison value", cause = error)
    }
}

internal data class NativeAccess(val keyExpressions: List<SqlExpr>?, val filters: List<NativePredicate>)
internal data class NativePredicate(val column: CatalogColumn, val operator: String, val expression: SqlExpr)

/** Only conjunctions imply individual predicates. OR, NOT and nullable tests remain local. */
internal fun nativeAccess(plan: BoundQuery, source: BoundSource): NativeAccess {
    var keys: List<SqlExpr>? = null
    val filters = mutableListOf<NativePredicate>()
    fun constant(expression: SqlExpr, conditional: Boolean) =
        !containsColumn(expression) && !containsSubquery(expression) && !containsAggregate(expression) &&
            (!conditional || expression is SqlExpr.Literal || expression is SqlExpr.Parameter)
    fun visit(expression: SqlExpr, conditional: Boolean = false) {
        if (expression is SqlExpr.Binary && expression.operator == "AND") {
            visit(expression.left, conditional)
            // Computed operands may fail. Keep them in the residual predicate if a
            // preceding AND branch can skip them; literals and parameters stay indexable.
            visit(expression.right, conditional = true)
            return
        }
        if (expression is SqlExpr.In && !expression.negated) {
            val column = (expression.operand as? SqlExpr.Column)?.let { plan.references[it] }
            if (column?.sourceId == source.id && column.isKey && column.outputIndex == null && expression.values.all { constant(it, conditional) }) keys = expression.values
        }
        if (expression !is SqlExpr.Binary || expression.operator !in setOf("=", "<", "<=", ">", ">=")) return
        val left = (expression.left as? SqlExpr.Column)?.let { plan.references[it] }
        val right = (expression.right as? SqlExpr.Column)?.let { plan.references[it] }
        val (column, value, operator) = when {
            left?.sourceId == source.id && constant(expression.right, conditional) -> Triple(left, expression.right, expression.operator)
            right?.sourceId == source.id && constant(expression.left, conditional) -> Triple(right, expression.left, when (expression.operator) { "<" -> ">"; "<=" -> ">="; ">" -> "<"; ">=" -> "<="; else -> "=" })
            else -> return
        }
        if (column.isKey && column.outputIndex == null) {
            if (operator == "=") keys = listOf(value)
        } else filters += NativePredicate(column, operator, value)
    }
    // Filtering a nullable right input using WHERE can turn an unmatched LEFT row into a match.
    // Keep such predicates entirely after joins. Single-table access remains maximally useful.
    if (plan.joins.isEmpty()) plan.query.where?.let { visit(it) }
    return NativeAccess(keys, filters)
}

/** A candidate is pushed only when the model's representation round-trips with SQL equality. */
@Suppress("UNCHECKED_CAST")
internal suspend fun NativeAccess.filter(table: SqlTable, evaluator: SqlEvaluator): IsFilter? {
    val pushed = mutableListOf<IsFilter>()
    for (predicate in filters) {
        // Store indexes order UTF-8 bytes; SQL text ordering is ordinal UTF-16. Equality is
        // portable, but range pushdown can discard supplementary Unicode characters incorrectly.
        if (predicate.column.column.type == SqlType.TEXT && predicate.operator != "=") continue
        val value = evaluator.eval(predicate.expression)
        if (value == SqlValue.Null) continue
        // Native floating comparisons can distinguish -0.0 from +0.0. SQL cannot, so
        // preserve both representations for equality and inclusive range boundaries.
        if (value is SqlValue.Float64 && value.value == 0.0) continue
        val definition = predicate.column.wrapper?.definition as? IsValueDefinition<Any, IsPropertyContext> ?: continue
        val native = try {
            when (value) {
                is SqlValue.Key -> Key<IsRootDataModel>(value.value.bytes)
                is SqlValue.Enum -> (predicate.column.wrapper.definition as EnumDefinition<*>).enum.resolve(value.name)
                else -> definition.fromString(value.display())
            }
        } catch (_: IllegalArgumentException) { null } catch (_: ParseException) { null } ?: continue
        val roundtrip = predicate.column.fromNative(native)
        if (sqlCompare(value, roundtrip) != 0) continue
        val reference = table.model.getPropertyReferenceByName(predicate.column.column.name) as IsPropertyReference<Any, IsValueDefinitionWrapper<Any, *, IsPropertyContext, *>, *>
        val pair = reference with native
        pushed += when (predicate.operator) {
            "=" -> Equals(pair)
            "<" -> LessThan(pair)
            "<=" -> LessThanEquals(pair)
            ">" -> GreaterThan(pair)
            else -> GreaterThanEquals(pair)
        }
    }
    return when (pushed.size) { 0 -> null; 1 -> pushed.single(); else -> And(pushed) }
}

package maryk.sql.syntax

internal data class SqlName(
    val text: String,
    val quoted: Boolean = false,
) {
    val normalized: String
        get() = if (quoted) text else text.asciiLowercase()
}

internal data class SqlQuery(
    val select: List<SqlSelect>,
    val from: SqlTableSource?,
    val where: SqlExpr?,
    val groupBy: List<SqlExpr>,
    val having: SqlExpr?,
    val orderBy: List<SqlOrder>,
    val limit: SqlExpr?,
    val offset: SqlExpr?,
    val distinct: Boolean,
    val parameterCount: Int,
    val explain: Boolean = false,
    val analyze: Boolean = false,
    val ctes: List<SqlCte> = emptyList(),
    val setOperation: SqlSetOperation? = null,
    val values: List<List<SqlExpr>>? = null,
    val body: SqlQuery? = null,
    val windows: List<Pair<SqlName, SqlWindowSpec>> = emptyList(),
)

internal enum class SqlMutationKind { INSERT, UPDATE, DELETE, UNDELETE, HARD_DELETE }

internal data class SqlMutation(
    val kind: SqlMutationKind,
    val table: List<SqlName>,
    val columns: List<SqlName> = emptyList(),
    val rows: List<List<SqlExpr>> = emptyList(),
    val assignments: List<Pair<SqlName, SqlExpr>> = emptyList(),
    val where: SqlExpr? = null,
    val parameterCount: Int,
    val sourceQuery: SqlQuery? = null,
    val returning: List<SqlSelect> = emptyList(),
    val conflict: SqlConflict? = null,
    val alias: SqlName? = null,
    val joinedSource: SqlTableSource? = null,
)

internal data class SqlCte(
    val name: SqlName,
    val columns: List<SqlName>,
    val query: SqlQuery,
)

internal data class SqlSetOperation(
    val left: SqlQuery,
    val operator: SqlSetOperator,
    val right: SqlQuery,
    val all: Boolean,
)

internal enum class SqlSetOperator {
    UNION,
    INTERSECT,
    EXCEPT,
}

internal data class SqlTableSource(
    val name: List<SqlName>,
    val alias: SqlName?,
    val derived: SqlQuery? = null,
    val joins: List<SqlJoin> = emptyList(),
    val unnest: SqlExpr? = null,
    val withOrdinality: Boolean = false,
)

internal data class SqlJoin(
    val right: SqlTableSource,
    val type: SqlJoinType,
    val condition: SqlExpr?,
)

internal enum class SqlJoinType {
    INNER,
    LEFT,
    CROSS,
}

internal data class SqlSelect(
    val expression: SqlExpr,
    val alias: SqlName?,
)

internal data class SqlOrder(
    val expression: SqlExpr,
    val ascending: Boolean,
    val nullsFirst: Boolean?,
)

internal sealed interface SqlExpr {
    val position: Int

    data class Literal(
        val text: String,
        val kind: LiteralKind,
        override val position: Int,
    ) : SqlExpr

    data class Column(
        val path: List<SqlName>,
        override val position: Int,
    ) : SqlExpr

    data class Parameter(
        val index: Int,
        override val position: Int,
    ) : SqlExpr

    data class Star(
        val qualifier: SqlName?,
        override val position: Int,
    ) : SqlExpr

    data class Unary(
        val operator: String,
        val operand: SqlExpr,
        override val position: Int,
    ) : SqlExpr

    data class Binary(
        val operator: String,
        val left: SqlExpr,
        val right: SqlExpr,
        override val position: Int,
    ) : SqlExpr

    data class Is(
        val operand: SqlExpr,
        val test: String,
        val negated: Boolean,
        override val position: Int,
    ) : SqlExpr

    data class In(
        val operand: SqlExpr,
        val values: List<SqlExpr>,
        val negated: Boolean,
        override val position: Int,
    ) : SqlExpr

    data class Between(
        val operand: SqlExpr,
        val lower: SqlExpr,
        val upper: SqlExpr,
        val negated: Boolean,
        override val position: Int,
    ) : SqlExpr

    data class Like(
        val operand: SqlExpr,
        val pattern: SqlExpr,
        val escape: SqlExpr?,
        val negated: Boolean,
        override val position: Int,
    ) : SqlExpr

    data class Call(
        val name: SqlName,
        val arguments: List<SqlExpr>,
        val distinct: Boolean,
        val filter: SqlExpr?,
        override val position: Int,
    ) : SqlExpr

    data class Window(
        val call: Call,
        val partitionBy: List<SqlExpr>,
        val orderBy: List<SqlOrder>,
        val frame: SqlWindowFrame = SqlWindowFrame.DEFAULT,
        override val position: Int,
        val windowName: SqlName? = null,
    ) : SqlExpr

    data class Cast(
        val operand: SqlExpr,
        val type: SqlCastType,
        override val position: Int,
    ) : SqlExpr

    data class Case(
        val operand: SqlExpr?,
        val branches: List<Pair<SqlExpr, SqlExpr>>,
        val otherwise: SqlExpr?,
        override val position: Int,
    ) : SqlExpr

    data class Subquery(
        val query: SqlQuery,
        override val position: Int,
    ) : SqlExpr

    data class Exists(
        val query: SqlQuery,
        val negated: Boolean,
        override val position: Int,
    ) : SqlExpr

    data class InQuery(
        val operand: SqlExpr,
        val query: SqlQuery,
        val negated: Boolean,
        override val position: Int,
    ) : SqlExpr
}

internal enum class LiteralKind {
    STRING,
    NUMBER,
    NULL,
    BOOLEAN,
    DATE,
    TIME,
    TIMESTAMP,
}

internal data class SqlCastType(
    val name: String,
    val precision: Int? = null,
    val scale: Int? = null,
)

private fun String.asciiLowercase(): String = buildString(length) {
    for (character in this@asciiLowercase) {
        append(if (character in 'A'..'Z') character + ('a' - 'A') else character)
    }
}

internal data class SqlConflict(val columns: List<SqlName>, val assignments: List<Pair<SqlName, SqlExpr>> = emptyList())

/** Preserve immutable syntax while rewriting column references within one scalar expression. */
internal fun SqlExpr.mapColumns(transform: (SqlExpr.Column) -> SqlExpr): SqlExpr = when (this) {
    is SqlExpr.Column -> transform(this)
    is SqlExpr.Unary -> copy(operand = operand.mapColumns(transform))
    is SqlExpr.Binary -> copy(left = left.mapColumns(transform), right = right.mapColumns(transform))
    is SqlExpr.Is -> copy(operand = operand.mapColumns(transform))
    is SqlExpr.In -> copy(operand = operand.mapColumns(transform), values = values.map { it.mapColumns(transform) })
    is SqlExpr.Between -> copy(operand = operand.mapColumns(transform), lower = lower.mapColumns(transform), upper = upper.mapColumns(transform))
    is SqlExpr.Like -> copy(operand = operand.mapColumns(transform), pattern = pattern.mapColumns(transform), escape = escape?.mapColumns(transform))
    is SqlExpr.Call -> copy(arguments = arguments.map { it.mapColumns(transform) }, filter = filter?.mapColumns(transform))
    is SqlExpr.Window -> copy(call = call.mapColumns(transform) as SqlExpr.Call, partitionBy = partitionBy.map { it.mapColumns(transform) }, orderBy = orderBy.map { it.copy(expression = it.expression.mapColumns(transform)) })
    is SqlExpr.Cast -> copy(operand = operand.mapColumns(transform))
    is SqlExpr.Case -> copy(operand = operand?.mapColumns(transform), branches = branches.map { it.first.mapColumns(transform) to it.second.mapColumns(transform) }, otherwise = otherwise?.mapColumns(transform))
    is SqlExpr.InQuery -> copy(operand = operand.mapColumns(transform))
    else -> this
}

internal enum class SqlFrameUnit { ROWS, RANGE }
internal enum class SqlBoundKind { UNBOUNDED_PRECEDING, PRECEDING, CURRENT, FOLLOWING, UNBOUNDED_FOLLOWING }
internal data class SqlWindowBound(val kind: SqlBoundKind, val offset: SqlExpr? = null)
internal data class SqlWindowFrame(val unit: SqlFrameUnit, val start: SqlWindowBound, val end: SqlWindowBound) {
    companion object {
        val DEFAULT = SqlWindowFrame(SqlFrameUnit.RANGE, SqlWindowBound(SqlBoundKind.UNBOUNDED_PRECEDING), SqlWindowBound(SqlBoundKind.CURRENT))
        val RUNNING_ROWS = SqlWindowFrame(SqlFrameUnit.ROWS, SqlWindowBound(SqlBoundKind.UNBOUNDED_PRECEDING), SqlWindowBound(SqlBoundKind.CURRENT))
        val FULL_ROWS = SqlWindowFrame(SqlFrameUnit.ROWS, SqlWindowBound(SqlBoundKind.UNBOUNDED_PRECEDING), SqlWindowBound(SqlBoundKind.UNBOUNDED_FOLLOWING))
    }
}
internal data class SqlWindowSpec(val partitionBy: List<SqlExpr>, val orderBy: List<SqlOrder>, val frame: SqlWindowFrame)

/** Transform windows within a scalar expression without crossing a subquery scope. */
internal fun SqlExpr.mapWindows(transform: (SqlExpr.Window) -> SqlExpr.Window): SqlExpr = when (this) {
    is SqlExpr.Window -> transform(this)
    is SqlExpr.Unary -> copy(operand = operand.mapWindows(transform))
    is SqlExpr.Binary -> copy(left = left.mapWindows(transform), right = right.mapWindows(transform))
    is SqlExpr.Is -> copy(operand = operand.mapWindows(transform))
    is SqlExpr.In -> copy(operand = operand.mapWindows(transform), values = values.map { it.mapWindows(transform) })
    is SqlExpr.Between -> copy(operand = operand.mapWindows(transform), lower = lower.mapWindows(transform), upper = upper.mapWindows(transform))
    is SqlExpr.Like -> copy(operand = operand.mapWindows(transform), pattern = pattern.mapWindows(transform), escape = escape?.mapWindows(transform))
    is SqlExpr.Call -> copy(arguments = arguments.map { it.mapWindows(transform) }, filter = filter?.mapWindows(transform))
    is SqlExpr.Cast -> copy(operand = operand.mapWindows(transform))
    is SqlExpr.Case -> copy(operand = operand?.mapWindows(transform), branches = branches.map { it.first.mapWindows(transform) to it.second.mapWindows(transform) }, otherwise = otherwise?.mapWindows(transform))
    is SqlExpr.InQuery -> copy(operand = operand.mapWindows(transform))
    else -> this
}

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

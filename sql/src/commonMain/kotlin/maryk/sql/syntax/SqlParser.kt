package maryk.sql.syntax

internal class SqlParser(
    private val text: String,
    private val maxTokens: Int = DEFAULT_MAX_TOKENS,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
    private val maxParameters: Int = DEFAULT_MAX_PARAMETERS,
) {
    private lateinit var tokens: List<SqlToken>
    private var tokenIndex = 0
    private var parameterCount = 0
    private var nestingDepth = 0
    private var astNodeCount = 0

    fun parse(): SqlQuery {
        if (maxDepth < 1) fail("Depth limit must be positive", 0)
        if (maxParameters < 0) fail("Parameter limit must not be negative", 0)
        tokens = SqlLexer(text, maxTokens).tokenize()

        val explain = matchKeyword("EXPLAIN")
        val analyze = explain && matchKeyword("ANALYZE")
        val parsed = parseQueryExpression()

        matchSymbol(";")
        if (current.kind != SqlTokenKind.EOF) {
            fail("Unexpected token '${current.text}'", current.position)
        }
        return parsed.copy(
            parameterCount = parameterCount,
            explain = explain,
            analyze = analyze,
        )
    }

    private fun parseQueryExpression(): SqlQuery {
        val ctes = if (matchKeyword("WITH")) parseCommonTableExpressions() else emptyList()
        var query = parseUnionOrExcept()
        query = parseQueryTail(query)
        if (ctes.isNotEmpty()) query = query.copy(ctes = ctes)
        return query
    }

    private fun parseCommonTableExpressions(): List<SqlCte> {
        if (matchKeyword("RECURSIVE")) fail("Recursive common table expressions are not supported", previous.position)
        return parseCommaSeparated("common table expression") {
            val name = parseName("common table expression name")
            val columns = if (matchSymbol("(")) {
                nested(previous.position) {
                    val result = parseCommaSeparated("common table expression column") {
                        parseName("common table expression column")
                    }
                    expectSymbol(")")
                    result
                }
            } else {
                emptyList()
            }
            expectKeyword("AS")
            expectSymbol("(")
            val query = nested(previous.position) {
                val result = parseQueryExpression()
                expectSymbol(")")
                result
            }
            SqlCte(name, columns, query)
        }
    }

    private fun parseUnionOrExcept(): SqlQuery {
        var query = parseIntersect()
        while (current.isKeyword("UNION") || current.isKeyword("EXCEPT")) {
            val operator = if (matchKeyword("UNION")) SqlSetOperator.UNION else {
                expectKeyword("EXCEPT")
                SqlSetOperator.EXCEPT
            }
            val all = parseSetQuantifier()
            query = setQuery(query, operator, parseIntersect(), all)
        }
        return query
    }

    private fun parseIntersect(): SqlQuery {
        var query = parseQueryTerm()
        while (matchKeyword("INTERSECT")) {
            val all = parseSetQuantifier()
            query = setQuery(query, SqlSetOperator.INTERSECT, parseQueryTerm(), all)
        }
        return query
    }

    private fun parseSetQuantifier(): Boolean = when {
        matchKeyword("ALL") -> true
        matchKeyword("DISTINCT") -> false
        else -> false
    }

    private fun parseQueryTerm(): SqlQuery = when {
        matchKeyword("SELECT") -> parseSelect()
        matchKeyword("VALUES") -> parseValues()
        matchSymbol("(") -> nested(previous.position) {
            val query = parseQueryExpression()
            expectSymbol(")")
            query
        }
        else -> fail("Expected SELECT or VALUES", current.position)
    }

    private fun parseSelect(): SqlQuery {
        val distinct = matchKeyword("DISTINCT")
        val select = parseCommaSeparated("select expression") {
            val expression = parseExpression().expression
            val alias = parseOptionalAlias("select alias")
            SqlSelect(expression, alias)
        }

        val from = if (matchKeyword("FROM")) parseTableSource() else null
        val where = if (matchKeyword("WHERE")) parseExpression().expression else null
        val groupBy = if (matchKeyword("GROUP")) {
            expectKeyword("BY")
            parseCommaSeparated("GROUP BY expression") { parseExpression().expression }
        } else {
            emptyList()
        }
        val having = if (matchKeyword("HAVING")) parseExpression().expression else null

        return SqlQuery(
            select = select,
            from = from,
            where = where,
            groupBy = groupBy,
            having = having,
            orderBy = emptyList(),
            limit = null,
            offset = null,
            distinct = distinct,
            parameterCount = parameterCount,
        )
    }

    private fun parseValues(): SqlQuery {
        val rows = mutableListOf<List<SqlExpr>>()
        var width: Int? = null
        do {
            expectSymbol("(")
            val row = nested(previous.position) {
                val values = parseCommaSeparated("VALUES expression") { parseExpression().expression }
                expectSymbol(")")
                values
            }
            if (width != null && row.size != width) fail("VALUES rows must have equal size", previous.position)
            width = row.size
            rows += row
        } while (matchSymbol(","))

        return emptyQuery().copy(values = rows)
    }

    private fun setQuery(left: SqlQuery, operator: SqlSetOperator, right: SqlQuery, all: Boolean): SqlQuery =
        emptyQuery().copy(setOperation = SqlSetOperation(left, operator, right, all))

    private fun parseQueryTail(query: SqlQuery): SqlQuery {
        val orderBy = if (matchKeyword("ORDER")) {
            expectKeyword("BY")
            parseCommaSeparated("ORDER BY expression") { parseOrder() }
        } else {
            emptyList()
        }

        var limit: SqlExpr? = null
        var offset: SqlExpr? = null
        if (matchKeyword("LIMIT")) limit = parseExpression().expression
        if (matchKeyword("OFFSET")) offset = parseExpression().expression
        if (matchKeyword("FETCH")) {
            if (limit != null) fail("LIMIT and FETCH cannot both be specified", previous.position)
            expectKeyword("FIRST")
            limit = parseExpression().expression
            if (!matchKeyword("ROW")) expectKeyword("ROWS")
            expectKeyword("ONLY")
        }
        return query.copy(orderBy = orderBy, limit = limit, offset = offset, parameterCount = parameterCount)
    }

    private fun emptyQuery(): SqlQuery = SqlQuery(
        select = emptyList(),
        from = null,
        where = null,
        groupBy = emptyList(),
        having = null,
        orderBy = emptyList(),
        limit = null,
        offset = null,
        distinct = false,
        parameterCount = parameterCount,
    )

    private fun parseTableSource(): SqlTableSource {
        val source = parseSingleTableSource()
        val joins = mutableListOf<SqlJoin>()
        while (true) {
            val type = when {
                matchKeyword("JOIN") -> SqlJoinType.INNER
                matchKeyword("INNER") -> {
                    expectKeyword("JOIN")
                    SqlJoinType.INNER
                }
                matchKeyword("LEFT") -> {
                    matchKeyword("OUTER")
                    expectKeyword("JOIN")
                    SqlJoinType.LEFT
                }
                matchKeyword("CROSS") -> {
                    expectKeyword("JOIN")
                    SqlJoinType.CROSS
                }
                else -> break
            }
            val right = parseSingleTableSource()
            val condition = if (type == SqlJoinType.CROSS) {
                null
            } else {
                expectKeyword("ON")
                parseExpression().expression
            }
            joins += SqlJoin(right, type, condition)
        }
        return source.copy(joins = joins)
    }

    private fun parseSingleTableSource(): SqlTableSource {
        if (matchSymbol("(")) {
            val derived = nested(previous.position) {
                val query = parseQueryExpression()
                expectSymbol(")")
                query
            }
            val alias = parseOptionalAlias("derived table alias")
                ?: fail("Derived table requires an alias", current.position)
            return SqlTableSource(emptyList(), alias, derived)
        }

        val name = mutableListOf(parseName("table name"))
        while (matchSymbol(".")) name += parseName("table name")
        val alias = parseOptionalAlias("table alias")
        return SqlTableSource(name, alias)
    }

    private fun parseOptionalAlias(description: String): SqlName? {
        if (matchKeyword("AS")) return parseName(description)
        if (current.kind == SqlTokenKind.QUOTED_IDENTIFIER) return advance().toName()
        if (current.kind == SqlTokenKind.IDENTIFIER && ALIAS_RESERVED_KEYWORDS.none(current::isKeyword)) {
            return advance().toName()
        }
        return null
    }

    private fun parseOrder(): SqlOrder {
        val expression = parseExpression().expression
        val ascending = when {
            matchKeyword("ASC") -> true
            matchKeyword("DESC") -> false
            else -> true
        }
        val nullsFirst = if (matchKeyword("NULLS")) {
            when {
                matchKeyword("FIRST") -> true
                matchKeyword("LAST") -> false
                else -> fail("Expected FIRST or LAST after NULLS", current.position)
            }
        } else {
            null
        }
        return SqlOrder(expression, ascending, nullsFirst)
    }

    private fun parseExpression(): ExpressionNode = parseOr()

    private fun parseOr(): ExpressionNode {
        var expression = parseAnd()
        while (matchKeyword("OR")) expression = binary("OR", expression, parseAnd())
        return expression
    }

    private fun parseAnd(): ExpressionNode {
        var expression = parseNot()
        while (matchKeyword("AND")) expression = binary("AND", expression, parseNot())
        return expression
    }

    private fun parseNot(): ExpressionNode {
        if (!matchKeyword("NOT")) return parseComparison()
        val position = previous.position
        if (current.isKeyword("EXISTS")) return parseExists(negated = true, position)
        val operand = parseNot()
        return node(SqlExpr.Unary("NOT", operand.expression, position), operand.depth + 1)
    }

    private fun parseComparison(): ExpressionNode {
        val operand = parseConcatenation()

        if (matchKeyword("IS")) {
            val negated = matchKeyword("NOT")
            if (matchKeyword("DISTINCT")) {
                expectKeyword("FROM")
                val right = parseConcatenation()
                val operator = if (negated) "IS NOT DISTINCT FROM" else "IS DISTINCT FROM"
                return binary(operator, operand, right)
            }
            val test = when {
                matchKeyword("NULL") -> "NULL"
                matchKeyword("TRUE") -> "TRUE"
                matchKeyword("FALSE") -> "FALSE"
                matchKeyword("UNKNOWN") -> "UNKNOWN"
                else -> fail("Expected NULL, TRUE, FALSE, UNKNOWN, or DISTINCT after IS", current.position)
            }
            return node(SqlExpr.Is(operand.expression, test, negated, operand.expression.position), operand.depth + 1)
        }

        var negated = false
        if (current.isKeyword("NOT") && (
                peek(1).isKeyword("IN") || peek(1).isKeyword("BETWEEN") || peek(1).isKeyword("LIKE")
            )
        ) {
            advance()
            negated = true
        }
        return when {
            matchKeyword("IN") -> parseIn(operand, negated)
            matchKeyword("BETWEEN") -> parseBetween(operand, negated)
            matchKeyword("LIKE") -> parseLike(operand, negated)
            current.kind == SqlTokenKind.SYMBOL && current.text in COMPARISON_OPERATORS -> {
                val operator = advance().text
                binary(operator, operand, parseConcatenation())
            }
            else -> operand
        }
    }

    private fun parseIn(operand: ExpressionNode, negated: Boolean): ExpressionNode {
        expectSymbol("(")
        return nested(previous.position) {
            if (isQueryStart()) {
                val query = parseQueryExpression()
                expectSymbol(")")
                return@nested node(
                    SqlExpr.InQuery(operand.expression, query, negated, operand.expression.position),
                    operand.depth + 1,
                )
            }
            if (matchSymbol(")")) fail("IN requires at least one value", previous.position)
            val values = parseCommaSeparated("IN value") { parseExpression() }
            expectSymbol(")")
            val depth = maxOf(operand.depth, values.maxOf { it.depth }) + 1
            node(
                SqlExpr.In(operand.expression, values.map { it.expression }, negated, operand.expression.position),
                depth,
            )
        }
    }

    private fun parseBetween(operand: ExpressionNode, negated: Boolean): ExpressionNode {
        val lower = parseConcatenation()
        expectKeyword("AND")
        val upper = parseConcatenation()
        return node(
            SqlExpr.Between(operand.expression, lower.expression, upper.expression, negated, operand.expression.position),
            maxOf(operand.depth, lower.depth, upper.depth) + 1,
        )
    }

    private fun parseLike(operand: ExpressionNode, negated: Boolean): ExpressionNode {
        val pattern = parseConcatenation()
        val escape = if (matchKeyword("ESCAPE")) parseConcatenation() else null
        return node(
            SqlExpr.Like(
                operand.expression,
                pattern.expression,
                escape?.expression,
                negated,
                operand.expression.position,
            ),
            maxOf(operand.depth, pattern.depth, escape?.depth ?: 0) + 1,
        )
    }

    private fun parseConcatenation(): ExpressionNode {
        var expression = parseAdditive()
        while (matchSymbol("||")) expression = binary("||", expression, parseAdditive())
        return expression
    }

    private fun parseAdditive(): ExpressionNode {
        var expression = parseMultiplicative()
        while (current.kind == SqlTokenKind.SYMBOL && current.text in ADDITIVE_OPERATORS) {
            val operator = advance().text
            expression = binary(operator, expression, parseMultiplicative())
        }
        return expression
    }

    private fun parseMultiplicative(): ExpressionNode {
        var expression = parseUnary()
        while (current.kind == SqlTokenKind.SYMBOL && current.text in MULTIPLICATIVE_OPERATORS) {
            val operator = advance().text
            expression = binary(operator, expression, parseUnary())
        }
        return expression
    }

    private fun parseUnary(): ExpressionNode {
        if (current.kind != SqlTokenKind.SYMBOL || current.text !in UNARY_OPERATORS) return parsePrimary()
        val operator = advance()
        val operand = parseUnary()
        return node(SqlExpr.Unary(operator.text, operand.expression, operator.position), operand.depth + 1)
    }

    private fun parsePrimary(): ExpressionNode {
        val token = current
        return when {
            matchSymbol("(") -> nested(token.position) {
                if (isQueryStart()) {
                    val query = parseQueryExpression()
                    expectSymbol(")")
                    leaf(SqlExpr.Subquery(query, token.position))
                } else {
                    val expression = parseExpression()
                    expectSymbol(")")
                    expression
                }
            }
            token.kind == SqlTokenKind.NUMBER -> {
                advance()
                leaf(SqlExpr.Literal(token.text, LiteralKind.NUMBER, token.position))
            }
            token.kind == SqlTokenKind.STRING -> {
                advance()
                leaf(SqlExpr.Literal(token.text, LiteralKind.STRING, token.position))
            }
            token.kind == SqlTokenKind.PARAMETER -> parseParameter()
            matchSymbol("*") -> leaf(SqlExpr.Star(null, token.position))
            token.isKeyword("NULL") -> keywordLiteral(LiteralKind.NULL)
            token.isKeyword("TRUE") || token.isKeyword("FALSE") -> keywordLiteral(LiteralKind.BOOLEAN)
            token.isKeyword("DATE") -> temporalLiteral(LiteralKind.DATE)
            token.isKeyword("TIME") -> temporalLiteral(LiteralKind.TIME)
            token.isKeyword("TIMESTAMP") -> temporalLiteral(LiteralKind.TIMESTAMP)
            token.isKeyword("CAST") -> parseCast()
            token.isKeyword("CASE") -> parseCase()
            token.isKeyword("EXISTS") -> parseExists(negated = false, token.position)
            token.kind == SqlTokenKind.IDENTIFIER || token.kind == SqlTokenKind.QUOTED_IDENTIFIER -> parseNameExpression()
            else -> fail("Expected expression", token.position)
        }
    }

    private fun parseExists(negated: Boolean, position: Int): ExpressionNode {
        expectKeyword("EXISTS")
        expectSymbol("(")
        return nested(previous.position) {
            if (!isQueryStart()) fail("EXISTS requires a query", current.position)
            val query = parseQueryExpression()
            expectSymbol(")")
            leaf(SqlExpr.Exists(query, negated, position))
        }
    }

    private fun parseParameter(): ExpressionNode {
        val token = advance()
        if (parameterCount >= maxParameters) {
            fail("Parameter limit of $maxParameters exceeded", token.position)
        }
        return leaf(SqlExpr.Parameter(parameterCount++, token.position))
    }

    private fun keywordLiteral(kind: LiteralKind): ExpressionNode {
        val token = advance()
        return leaf(SqlExpr.Literal(token.text.uppercaseAscii(), kind, token.position))
    }

    private fun temporalLiteral(kind: LiteralKind): ExpressionNode {
        val keyword = advance()
        val value = expect(SqlTokenKind.STRING, "string literal after ${keyword.text.uppercaseAscii()}")
        return leaf(SqlExpr.Literal(value.text, kind, keyword.position))
    }

    private fun parseNameExpression(): ExpressionNode {
        val firstToken = advance()
        val firstName = firstToken.toName()
        if (matchSymbol("(")) return parseCall(firstName, firstToken.position)

        val path = mutableListOf(firstName)
        while (matchSymbol(".")) {
            if (matchSymbol("*")) {
                if (path.size != 1) fail("Qualified star supports one qualifier", previous.position)
                return leaf(SqlExpr.Star(path.single(), firstToken.position))
            }
            path += parseName("column name")
        }
        return leaf(SqlExpr.Column(path, firstToken.position))
    }

    private fun parseCall(name: SqlName, position: Int): ExpressionNode = nested(position) {
        val distinct = matchKeyword("DISTINCT")
        val arguments = mutableListOf<ExpressionNode>()
        if (!matchSymbol(")")) {
            arguments += parseExpression()
            while (matchSymbol(",")) arguments += parseExpression()
            expectSymbol(")")
        }
        if (distinct && arguments.isEmpty()) fail("DISTINCT requires a function argument", position)

        val filter = if (matchKeyword("FILTER")) {
            expectSymbol("(")
            nested(previous.position) {
                expectKeyword("WHERE")
                val expression = parseExpression()
                expectSymbol(")")
                expression
            }
        } else {
            null
        }
        val depth = maxOf(arguments.maxOfOrNull { it.depth } ?: 0, filter?.depth ?: 0) + 1
        node(SqlExpr.Call(name, arguments.map { it.expression }, distinct, filter?.expression, position), depth)
    }

    private fun parseCast(): ExpressionNode {
        val cast = advance()
        expectSymbol("(")
        return nested(cast.position) {
            val operand = parseExpression()
            expectKeyword("AS")
            val typeToken = expectNameToken("cast type")
            var precision: Int? = null
            var scale: Int? = null
            if (matchSymbol("(")) {
                nested(previous.position) {
                    precision = parseUnsignedInt("cast precision")
                    if (matchSymbol(",")) scale = parseUnsignedInt("cast scale")
                    expectSymbol(")")
                }
            }
            expectSymbol(")")
            val typeName = if (typeToken.kind == SqlTokenKind.QUOTED_IDENTIFIER) {
                typeToken.text
            } else {
                typeToken.text.uppercaseAscii()
            }
            node(
                SqlExpr.Cast(operand.expression, SqlCastType(typeName, precision, scale), cast.position),
                operand.depth + 1,
            )
        }
    }

    private fun parseCase(): ExpressionNode {
        val case = advance()
        return nested(case.position) {
            val operand = if (current.isKeyword("WHEN")) null else parseExpression()
            val branches = mutableListOf<Pair<ExpressionNode, ExpressionNode>>()
            while (matchKeyword("WHEN")) {
                val condition = parseExpression()
                expectKeyword("THEN")
                val result = parseExpression()
                branches += condition to result
            }
            if (branches.isEmpty()) fail("CASE requires at least one WHEN branch", current.position)
            val otherwise = if (matchKeyword("ELSE")) parseExpression() else null
            expectKeyword("END")

            var childDepth = operand?.depth ?: 0
            for ((condition, result) in branches) childDepth = maxOf(childDepth, condition.depth, result.depth)
            childDepth = maxOf(childDepth, otherwise?.depth ?: 0)
            node(
                SqlExpr.Case(
                    operand?.expression,
                    branches.map { it.first.expression to it.second.expression },
                    otherwise?.expression,
                    case.position,
                ),
                childDepth + 1,
            )
        }
    }

    private fun parseUnsignedInt(description: String): Int {
        val token = expect(SqlTokenKind.NUMBER, description)
        if (token.text.any { !it.isDigit() }) fail("Expected integer $description", token.position)
        return token.text.toIntOrNull() ?: fail("$description is too large", token.position)
    }

    private fun parseName(description: String): SqlName = expectNameToken(description).toName()

    private fun expectNameToken(description: String): SqlToken {
        if (current.kind != SqlTokenKind.IDENTIFIER && current.kind != SqlTokenKind.QUOTED_IDENTIFIER) {
            fail("Expected $description", current.position)
        }
        return advance()
    }

    private fun SqlToken.toName(): SqlName = SqlName(text, kind == SqlTokenKind.QUOTED_IDENTIFIER)

    private fun binary(operator: String, left: ExpressionNode, right: ExpressionNode): ExpressionNode =
        node(
            SqlExpr.Binary(operator, left.expression, right.expression, left.expression.position),
            maxOf(left.depth, right.depth) + 1,
        )

    private fun leaf(expression: SqlExpr): ExpressionNode = node(expression, 1)

    private fun node(expression: SqlExpr, depth: Int): ExpressionNode {
        if (depth > maxDepth) fail("AST depth limit of $maxDepth exceeded", expression.position)
        astNodeCount++
        if (astNodeCount > maxTokens) fail("AST node limit of $maxTokens exceeded", expression.position)
        return ExpressionNode(expression, depth)
    }

    private inline fun <T> nested(position: Int, block: () -> T): T {
        nestingDepth++
        if (nestingDepth > maxDepth) {
            nestingDepth--
            fail("Nesting depth limit of $maxDepth exceeded", position)
        }
        return try {
            block()
        } finally {
            nestingDepth--
        }
    }

    private inline fun <T> parseCommaSeparated(description: String, parseItem: () -> T): List<T> {
        if (current.kind == SqlTokenKind.EOF || current.text == ";") {
            fail("Expected $description", current.position)
        }
        val values = mutableListOf(parseItem())
        while (matchSymbol(",")) values += parseItem()
        return values
    }

    private fun matchKeyword(keyword: String): Boolean {
        if (!current.isKeyword(keyword)) return false
        advance()
        return true
    }

    private fun isQueryStart(): Boolean =
        current.isKeyword("SELECT") || current.isKeyword("VALUES") || current.isKeyword("WITH")

    private fun expectKeyword(keyword: String) {
        if (!matchKeyword(keyword)) fail("Expected $keyword", current.position)
    }

    private fun matchSymbol(symbol: String): Boolean {
        if (current.kind != SqlTokenKind.SYMBOL || current.text != symbol) return false
        advance()
        return true
    }

    private fun expectSymbol(symbol: String) {
        if (!matchSymbol(symbol)) fail("Expected '$symbol'", current.position)
    }

    private fun expect(kind: SqlTokenKind, description: String): SqlToken {
        if (current.kind != kind) fail("Expected $description", current.position)
        return advance()
    }

    private fun advance(): SqlToken = current.also { if (tokenIndex < tokens.lastIndex) tokenIndex++ }

    private fun peek(offset: Int): SqlToken = tokens[(tokenIndex + offset).coerceAtMost(tokens.lastIndex)]

    private val current: SqlToken
        get() = tokens[tokenIndex]

    private val previous: SqlToken
        get() = tokens[(tokenIndex - 1).coerceAtLeast(0)]

    private fun fail(message: String, position: Int): Nothing =
        throw SqlParseException("$message at position $position", position)

    private data class ExpressionNode(
        val expression: SqlExpr,
        val depth: Int,
    )

    private companion object {
        const val DEFAULT_MAX_TOKENS = 16_384
        const val DEFAULT_MAX_DEPTH = 64
        const val DEFAULT_MAX_PARAMETERS = 1_024
        val COMPARISON_OPERATORS = setOf("=", "!=", "<>", "<", "<=", ">", ">=")
        val ADDITIVE_OPERATORS = setOf("+", "-")
        val MULTIPLICATIVE_OPERATORS = setOf("*", "/", "%")
        val UNARY_OPERATORS = setOf("+", "-")
        val ALIAS_RESERVED_KEYWORDS = setOf(
            "ANALYZE", "AND", "AS", "ASC", "BETWEEN", "BY", "CASE", "CAST", "CROSS", "DATE",
            "DESC", "DISTINCT", "ELSE", "END", "ESCAPE", "EXCEPT", "EXPLAIN", "FALSE", "FETCH",
            "FILTER", "FIRST", "FROM", "FULL", "GROUP", "HAVING", "IN", "INNER", "INTERSECT", "IS",
            "JOIN", "LAST", "LEFT", "LIKE", "LIMIT", "NOT", "NULL", "NULLS", "OFFSET", "ON", "ONLY",
            "OR", "ORDER", "OUTER", "OVER", "RIGHT", "ROW", "ROWS", "SELECT", "THEN", "TIME",
            "TIMESTAMP", "TRUE", "UNION", "UNKNOWN", "USING", "VALUES", "WHEN", "WHERE", "WITH",
        )
    }
}

internal class SqlParseException(
    message: String,
    val position: Int,
) : IllegalArgumentException(message)

private fun String.uppercaseAscii(): String = buildString(length) {
    for (character in this@uppercaseAscii) {
        append(if (character in 'a'..'z') character - ('a' - 'A') else character)
    }
}

package io.maryk.app.ui.sql

import maryk.sql.SqlCatalog
import maryk.sql.SqlTable
import maryk.sql.SqlType

internal enum class SqlTokenStyle { WORD, KEYWORD, FUNCTION, STRING, IDENTIFIER, NUMBER, COMMENT, PARAMETER, SYMBOL }
internal data class SqlEditorToken(val start: Int, val end: Int, val text: String, val style: SqlTokenStyle)
internal data class SqlCompletion(val label: String, val insertion: String, val detail: String, val start: Int, val end: Int)

private val keywords = setOf(
    "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "NULL", "TRUE", "FALSE", "AS", "DISTINCT", "ALL",
    "ORDER BY", "GROUP BY", "HAVING", "LIMIT", "OFFSET", "FETCH FIRST", "ROWS ONLY", "ASC", "DESC",
    "NULLS FIRST", "NULLS LAST", "INNER JOIN", "LEFT JOIN", "CROSS JOIN", "ON", "IN", "IS", "BETWEEN",
    "LIKE", "ESCAPE", "CASE", "WHEN", "THEN", "ELSE", "END", "WITH", "UNION", "INTERSECT", "EXCEPT",
    "EXISTS", "CAST", "EXPLAIN", "ANALYZE", "INSERT INTO", "VALUES", "UPDATE", "SET", "DELETE FROM",
    "UNDELETE FROM", "DELETE HARD FROM", "FILTER", "DATE", "TIME", "TIMESTAMP",
)
private val functions = listOf("COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE", "NULLIF", "ABS", "ROUND", "FLOOR", "CEIL", "CEILING", "LOWER", "UPPER", "LENGTH", "CHAR_LENGTH", "TRIM", "LTRIM", "RTRIM", "CONCAT", "SUBSTRING", "SUBSTR", "DATE_PART")
private val keywordWords = keywords.flatMap { it.split(' ') }.toSet()

/** Tolerant editor lexer: incomplete strings/comments remain highlightable while typing. */
internal fun sqlEditorTokens(text: String): List<SqlEditorToken> = buildList {
    var i = 0
    while (i < text.length) {
        if (text[i].isWhitespace()) { i++; continue }
        val start = i
        val style = when {
            text.startsWith("--", i) -> {
                i += 2
                while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
                SqlTokenStyle.COMMENT
            }
            text.startsWith("/*", i) -> {
                val end = text.indexOf("*/", i + 2)
                i = if (end < 0) text.length else end + 2
                SqlTokenStyle.COMMENT
            }
            text[i] == '\'' || text[i] == '"' -> {
                val quote = text[i++]
                while (i < text.length) {
                    if (text[i++] == quote) {
                        if (i < text.length && text[i] == quote) i++ else break
                    }
                }
                if (quote == '\'') SqlTokenStyle.STRING else SqlTokenStyle.IDENTIFIER
            }
            text[i] == '?' -> { i++; SqlTokenStyle.PARAMETER }
            text[i].isDigit() || text[i] == '.' && text.getOrNull(i + 1)?.isDigit() == true -> {
                while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
                if (text.getOrNull(i) in listOf('e', 'E')) {
                    i++
                    if (text.getOrNull(i) in listOf('+', '-')) i++
                    while (i < text.length && text[i].isDigit()) i++
                }
                SqlTokenStyle.NUMBER
            }
            text[i].isLetter() || text[i] == '_' -> {
                while (i < text.length && (text[i].isLetterOrDigit() || text[i] in "_$")) i++
                when (text.substring(start, i).uppercase()) {
                    in keywordWords -> SqlTokenStyle.KEYWORD
                    in functions -> SqlTokenStyle.FUNCTION
                    else -> SqlTokenStyle.WORD
                }
            }
            else -> { i++; SqlTokenStyle.SYMBOL }
        }
        add(SqlEditorToken(start, i, text.substring(start, i), style))
    }
}

internal fun sqlIdentifier(name: String): String = "\"${name.replace("\"", "\"\"")}\""
internal fun sqlTableName(table: SqlTable): String = "${sqlIdentifier(table.schema)}.${sqlIdentifier(table.name)}"
private fun SqlEditorToken.name(): String = if (style == SqlTokenStyle.IDENTIFIER) text.removeSurrounding("\"").replace("\"\"", "\"") else text

/** Suggestions are catalog-based; table aliases are recognized even in unfinished statements. */
internal fun sqlCompletions(text: String, caret: Int, catalog: SqlCatalog?, explicit: Boolean = false): List<SqlCompletion> {
    if (caret !in 0..text.length) return emptyList()
    val tokens = sqlEditorTokens(text)
    val atCaret = tokens.lastOrNull { it.start < caret && it.end >= caret }
    if (atCaret?.style in setOf(SqlTokenStyle.COMMENT, SqlTokenStyle.STRING)) return emptyList()
    val current = atCaret?.takeIf { it.style in setOf(SqlTokenStyle.WORD, SqlTokenStyle.KEYWORD, SqlTokenStyle.FUNCTION, SqlTokenStyle.IDENTIFIER) }
    val start = current?.start ?: caret
    val end = current?.end ?: caret
    val prefix = text.substring(start, caret).removePrefix("\"").replace("\"\"", "\"")
    val before = tokens.filter { it.end <= start && it.style != SqlTokenStyle.COMMENT }
    if (!explicit && prefix.isEmpty() && before.lastOrNull()?.text != ".") return emptyList()
    val tables = catalog?.tables.orEmpty()
    val aliases = mutableMapOf<String, SqlTable>()
    val sources = mutableListOf<Pair<SqlTable, String>>()
    for (index in tokens.indices) {
        if (tokens[index].style != SqlTokenStyle.KEYWORD || tokens[index].text.uppercase() !in setOf("FROM", "JOIN", "UPDATE", "INTO")) continue
        val name = tokens.getOrNull(index + 1) ?: continue
        var after = index + 2
        val schema = if (tokens.getOrNull(after)?.text == ".") name.name() else null
        val tableToken = if (schema != null) tokens.getOrNull(++after) ?: continue else name
        if (schema != null) after++
        val table = tables.singleOrNull { it.name.equals(tableToken.name(), true) && (schema == null || it.schema.equals(schema, true)) } ?: continue
        if (tokens.getOrNull(after)?.text.equals("AS", true)) after++
        val alias = tokens.getOrNull(after)?.takeIf { it.style in setOf(SqlTokenStyle.WORD, SqlTokenStyle.IDENTIFIER) }?.name()
        val qualifier = alias ?: table.name
        aliases[qualifier.lowercase()] = table
        sources += table to qualifier
    }
    val result = mutableListOf<SqlCompletion>()
    fun add(label: String, insertion: String, detail: String) {
        if (label.startsWith(prefix, ignoreCase = true)) result += SqlCompletion(label, insertion, detail, start, end)
    }
    fun columns(table: SqlTable, qualifier: String? = null) {
        table.columns.filter { it.type != SqlType.ANY }.forEach { column ->
            val path = column.name.split('.').joinToString(".", transform = ::sqlIdentifier)
            val insertion = if (qualifier != null) "${sqlIdentifier(qualifier)}.$path" else path
            val source = qualifier?.let { "$it · ${table.name}" } ?: table.name
            add(column.name, insertion, "$source · ${column.type}${if (column.nullable) " · nullable" else ""}")
        }
    }
    if (before.lastOrNull()?.text == ".") {
        val qualifier = before.getOrNull(before.lastIndex - 1)?.name().orEmpty()
        val table = aliases[qualifier.lowercase()] ?: tables.singleOrNull { it.name.equals(qualifier, true) }
        if (table != null) columns(table)
        else tables.filter { it.schema.equals(qualifier, true) }.forEach { add(it.name, sqlIdentifier(it.name), "Table · ${it.schema}") }
    } else if (before.lastOrNull()?.text?.uppercase() in setOf("FROM", "JOIN", "INTO", "UPDATE")) {
        tables.forEach { add(it.name, sqlTableName(it), "Table · ${it.schema}") }
    } else {
        if (sources.isEmpty()) tables.forEach { columns(it) }
        else sources.forEach { (table, qualifier) -> columns(table, qualifier.takeIf { sources.size > 1 }) }
        keywords.forEach { add(it, it, "Keyword") }
        functions.forEach { add(it, "$it(", "Function") }
        tables.forEach { add(it.name, sqlTableName(it), "Table · ${it.schema}") }
    }
    return result.distinctBy { it.insertion }.take(10)
}

internal fun applySqlCompletion(text: String, completion: SqlCompletion): Pair<String, Int> {
    val updated = text.replaceRange(completion.start, completion.end, completion.insertion)
    return updated to (completion.start + completion.insertion.length)
}

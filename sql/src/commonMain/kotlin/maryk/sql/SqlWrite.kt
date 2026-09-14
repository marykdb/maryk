package maryk.sql

import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.types.Key
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.sql.syntax.SqlParseException
import maryk.sql.syntax.validateSqlText

/** Outcome of one SQL mutation. Failed Maryk statuses are preserved as descriptive strings. */
data class SqlWriteResult(
    val affectedRows: Int,
    val failures: List<String> = emptyList(),
)

/**
 * First write surface: INSERT ... (columns) VALUES (...), UPDATE ... SET ... WHERE __key = ...,
 * and DELETE FROM ... WHERE __key = .... Writes are always one table and are delegated to normal
 * Maryk requests, so validation, authorization, versioning and store transactions stay intact.
 */
internal class SqlWriter(private val sql: MarykSql) {
    suspend fun execute(statement: String, parameters: List<SqlValue>): SqlWriteResult {
        try {
            validateSqlText(statement)
        } catch (error: SqlParseException) {
            throw SqlException(SqlErrorCode.SYNTAX, error.message ?: "Invalid SQL", cause = error)
        }
        val text = statement.trim().removeSuffix(";").trim()
        val keyword = text.takeWhile { !it.isWhitespace() }.uppercase()
        return when (keyword) {
            "INSERT" -> insert(text, parameters)
            "UPDATE" -> update(text, parameters)
            "DELETE" -> delete(text, parameters)
            else -> throw SqlException(SqlErrorCode.SYNTAX, "Expected INSERT, UPDATE, or DELETE")
        }
    }

    private suspend fun insert(text: String, parameters: List<SqlValue>): SqlWriteResult {
        val into = text.removePrefixIgnoreCase("INSERT INTO").trimStart()
        val open = into.indexOf('(')
        if (open < 1) throw SqlException(SqlErrorCode.SYNTAX, "Expected INSERT INTO table (columns) VALUES (values)")
        val close = matchingParen(into, open)
        val valuesAt = keywordIndex(into, "VALUES", close + 1)
        if (valuesAt < 0) throw SqlException(SqlErrorCode.SYNTAX, "Expected VALUES")
        val table = table(into.substring(0, open).trim())
        val columns = split(into.substring(open + 1, close)).map { column(table, it.trim()) }
        if (columns.isEmpty() || columns.distinctBy { it.column.name }.size != columns.size) throw SqlException(SqlErrorCode.BINDING, "INSERT columns must be unique")
        if (columns.any { it.indices.isEmpty() || it.indices.size != 1 }) throw SqlException(SqlErrorCode.UNSUPPORTED, "INSERT supports direct scalar model properties")
        val rows = parseValueRows(into.substring(valuesAt + "VALUES".length).trim())
        if (rows.any { it.size != columns.size }) throw SqlException(SqlErrorCode.SYNTAX, "INSERT VALUES width does not match column list")
        val evaluated = evaluate(rows.flatten(), parameters)
        var offset = 0
        val objects = rows.map {
            val values = evaluated.subList(offset, offset + columns.size)
            offset += columns.size
            if (values.any { it == SqlValue.Null }) throw SqlException(SqlErrorCode.UNSUPPORTED, "INSERT NULL is not supported yet")
            val pairs = columns.zip(values).map { (column, value) -> pair(column, value) }
            table.model.fromChanges(null, listOf(Change(*pairs.toTypedArray())))
        }
        val response = sql.dataStore.execute(table.model.add(*objects.toTypedArray()))
        return SqlWriteResult(response.statuses.count { it is AddSuccess<*> }, response.statuses.filterNot { it is AddSuccess<*> }.map { it.toString() })
    }

    private suspend fun update(text: String, parameters: List<SqlValue>): SqlWriteResult {
        val setAt = keywordIndex(text, "SET", "UPDATE".length)
        val whereAt = if (setAt < 0) -1 else keywordIndex(text, "WHERE", setAt + "SET".length)
        if (setAt < 0 || whereAt < 0) throw SqlException(SqlErrorCode.SYNTAX, "UPDATE requires SET assignments and WHERE __key = ?")
        val table = table(text.substring("UPDATE".length, setAt).trim())
        val assignments = split(text.substring(setAt + "SET".length, whereAt)).map { assignment ->
            val equals = topLevelEquals(assignment)
            if (equals < 0) throw SqlException(SqlErrorCode.SYNTAX, "Expected column = expression in SET")
            column(table, assignment.substring(0, equals).trim()) to assignment.substring(equals + 1).trim()
        }
        if (assignments.isEmpty() || assignments.any { it.first.indices.isEmpty() || it.first.indices.size != 1 }) throw SqlException(SqlErrorCode.UNSUPPORTED, "UPDATE supports direct scalar model properties")
        val target = target(text.substring(whereAt + "WHERE".length))
        val selected = sql.query(
            "SELECT __key, __version, " + assignments.joinToString(", ") { it.second } +
                " FROM ${quotedIdentifier(table.schema)}.${quotedIdentifier(table.name)} WHERE ${target.whereClause}",
            parameters,
        ).rows.singleOrNull() ?: return SqlWriteResult(0)
        val key = key(table, selected[0])
        val version = (selected[1] as? SqlValue.UInt64)?.value
            ?: throw SqlException(SqlErrorCode.EXECUTION_STATE, "Selected row has no version")
        val values = selected.values.drop(2)
        if (values.any { it == SqlValue.Null }) throw SqlException(SqlErrorCode.UNSUPPORTED, "UPDATE SET NULL is not supported yet")
        val pairs = assignments.zip(values).map { (assignment, value) -> pair(assignment.first, value) }
        val change = Change(*pairs.toTypedArray())
        val response = sql.dataStore.execute(table.model.change(key.change(change, lastVersion = version)))
        return SqlWriteResult(response.statuses.count { it is ChangeSuccess<*> }, response.statuses.filterNot { it is ChangeSuccess<*> }.map { it.toString() })
    }

    private suspend fun delete(text: String, parameters: List<SqlValue>): SqlWriteResult {
        val from = text.removePrefixIgnoreCase("DELETE FROM").trimStart()
        val whereAt = keywordIndex(from, "WHERE")
        if (whereAt < 0) throw SqlException(SqlErrorCode.SYNTAX, "DELETE requires WHERE __key = ?")
        val table = table(from.substring(0, whereAt).trim())
        val target = target(from.substring(whereAt + "WHERE".length))
        val selected = sql.query(
            "SELECT __key, __version FROM ${quotedIdentifier(table.schema)}.${quotedIdentifier(table.name)} WHERE ${target.whereClause}",
            parameters,
        ).rows.singleOrNull() ?: return SqlWriteResult(0)
        val key = key(table, selected[0])
        val version = (selected[1] as? SqlValue.UInt64)?.value
            ?: throw SqlException(SqlErrorCode.EXECUTION_STATE, "Selected row has no version")
        val response = sql.dataStore.execute(table.model.change(key.change(ObjectSoftDeleteChange(true), lastVersion = version)))
        return SqlWriteResult(response.statuses.count { it is ChangeSuccess<*> }, response.statuses.filterNot { it is ChangeSuccess<*> }.map { it.toString() })
    }

    private suspend fun evaluate(expressions: List<String>, parameters: List<SqlValue>): List<SqlValue> {
        val result = sql.query("SELECT " + expressions.joinToString(", "), parameters)
        return result.rows.singleOrNull()?.values ?: throw SqlException(SqlErrorCode.EXECUTION_STATE, "Expression evaluation did not return one row")
    }

    private fun table(name: String): SqlTable {
        val parts = splitQualifiedIdentifier(name)
        val schema = if (parts.size == 2) parts[0] else sql.options.defaultSchema
        val tableName = parts.lastOrNull() ?: throw SqlException(SqlErrorCode.SYNTAX, "Expected table name")
        return sql.catalog.tables.singleOrNull { asciiLower(it.schema) == asciiLower(schema) && asciiLower(it.name) == asciiLower(tableName) }
            ?: throw SqlException(SqlErrorCode.BINDING, "Unknown table '$name'")
    }

    private fun column(table: SqlTable, name: String): CatalogColumn =
        table.bindings.singleOrNull { asciiLower(it.column.name) == asciiLower(identifier(name)) }
            ?: throw SqlException(SqlErrorCode.BINDING, "Unknown column '$name' in table '${table.name}'")

    private fun target(where: String): WriteTarget {
        val expressions = splitTopLevelKeyword(where, "AND")
        if (expressions.size !in 1..2) throw SqlException(SqlErrorCode.UNSUPPORTED, "UPDATE and DELETE require WHERE __key = expression, optionally guarded by __version = expression")
        var keyExpression: String? = null
        var versionExpression: String? = null
        for (expression in expressions) {
            val equals = topLevelEquals(expression)
            if (equals < 0) throw SqlException(SqlErrorCode.SYNTAX, "Expected system column = expression in WHERE")
            val column = identifier(expression.substring(0, equals).trim())
            val value = expression.substring(equals + 1).trim()
            if (value.isEmpty()) throw SqlException(SqlErrorCode.SYNTAX, "Expected expression after =")
            when {
                column.equals("__key", true) && keyExpression == null -> keyExpression = value
                column.equals("__version", true) && versionExpression == null -> versionExpression = value
                else -> throw SqlException(SqlErrorCode.UNSUPPORTED, "UPDATE and DELETE only support __key and optional __version predicates")
            }
        }
        keyExpression ?: throw SqlException(SqlErrorCode.UNSUPPORTED, "UPDATE and DELETE require WHERE __key = expression")
        return WriteTarget(where.trim())
    }

    private fun key(table: SqlTable, value: SqlValue): Key<IsRootDataModel> {
        val binding = table.bindings.first { it.isKey }
        val typed = domainValue(value, binding, SqlErrorCode.TYPE) as? SqlValue.Key
            ?: throw SqlException(SqlErrorCode.TYPE, "WHERE __key requires a typed key parameter")
        return Key<IsRootDataModel>(typed.value.bytes)
    }

    private fun nativeValue(column: CatalogColumn, value: SqlValue): Any {
        if (column.column.type == SqlType.KEY) return (domainValue(value, column, SqlErrorCode.TYPE) as SqlValue.Key).value
        if (column.column.type == SqlType.ENUM) return (domainValue(value, column, SqlErrorCode.TYPE) as SqlValue.Enum).let { enum ->
            (column.wrapper!!.definition as maryk.core.properties.definitions.EnumDefinition<*>).enum.resolve(enum.name)
                ?: throw SqlException(SqlErrorCode.TYPE, "Unknown enum value '${enum.name}'")
        }
        @Suppress("UNCHECKED_CAST")
        val definition = column.wrapper!!.definition as IsValueDefinition<Any, maryk.core.properties.IsPropertyContext>
        return try { definition.fromString(value.display()) } catch (error: Throwable) {
            throw SqlException(SqlErrorCode.TYPE, "Invalid value for column '${column.column.name}'", cause = error)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun pair(column: CatalogColumn, value: SqlValue) =
        (column.wrapper!!.ref() as maryk.core.properties.references.IsPropertyReference<Any, maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper<Any, *, maryk.core.properties.IsPropertyContext, *>, *>) with nativeValue(column, value)

    private fun parseValueRows(values: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var index = 0
        while (index < values.length) {
            while (index < values.length && values[index].isWhitespace()) index++
            if (values.getOrNull(index) != '(') throw SqlException(SqlErrorCode.SYNTAX, "Expected VALUES row")
            val end = matchingParen(values, index)
            result += split(values.substring(index + 1, end))
            index = end + 1
            while (index < values.length && values[index].isWhitespace()) index++
            if (index == values.length) break
            if (values[index++] != ',') throw SqlException(SqlErrorCode.SYNTAX, "Expected comma between VALUES rows")
        }
        return result
    }

}

private data class WriteTarget(val whereClause: String)

private fun String.removePrefixIgnoreCase(prefix: String): String =
    if (length >= prefix.length && substring(0, prefix.length).equals(prefix, true)) substring(prefix.length)
    else throw SqlException(SqlErrorCode.SYNTAX, "Expected $prefix")

private fun quotedIdentifier(name: String): String = "\"${name.replace("\"", "\"\"")}\""

private fun splitQualifiedIdentifier(text: String): List<String> {
    val parts = mutableListOf<String>()
    var index = 0
    while (true) {
        while (text.getOrNull(index)?.isWhitespace() == true) index++
        if (index == text.length) throw SqlException(SqlErrorCode.SYNTAX, "Expected table name")
        val part = StringBuilder()
        if (text[index] == '"') {
            index++
            var closed = false
            while (index < text.length) {
                when (text[index++]) {
                    '"' -> if (text.getOrNull(index) == '"') {
                        part.append('"')
                        index++
                    } else {
                        closed = true
                        break
                    }
                    else -> part.append(text[index - 1])
                }
            }
            if (!closed) throw SqlException(SqlErrorCode.SYNTAX, "Unclosed quoted identifier")
            while (text.getOrNull(index)?.isWhitespace() == true) index++
        } else {
            val start = index
            while (text.getOrNull(index) != null && text[index] != '.') index++
            part.append(text.substring(start, index).trim())
            if (part.isEmpty() || part.any { it == '"' || it.isWhitespace() }) {
                throw SqlException(SqlErrorCode.SYNTAX, "Invalid table identifier")
            }
        }
        if (part.isEmpty()) throw SqlException(SqlErrorCode.SYNTAX, "Empty table identifier")
        parts += part.toString()
        if (index == text.length) break
        if (text[index++] != '.') throw SqlException(SqlErrorCode.SYNTAX, "Expected dot between schema and table")
    }
    if (parts.size !in 1..2) throw SqlException(SqlErrorCode.SYNTAX, "Expected table or schema.table")
    return parts
}

private fun identifier(text: String): String = splitQualifiedIdentifier(text).singleOrNull()
    ?: throw SqlException(SqlErrorCode.SYNTAX, "Expected column identifier")

private fun keywordIndex(text: String, keyword: String, start: Int = 0): Int {
    var depth = 0; var quote: Char? = null
    for (index in start..text.length - keyword.length) {
        val character = text[index]
        if (quote != null) { if (character == quote && text.getOrNull(index + 1) != quote) quote = null; continue }
        when (character) { '\'', '\"' -> quote = character; '(' -> depth++; ')' -> depth-- }
        if (depth == 0 && text.regionMatches(index, keyword, 0, keyword.length, ignoreCase = true) &&
            text.getOrNull(index - 1)?.isLetterOrDigit() != true && text.getOrNull(index + keyword.length)?.isLetterOrDigit() != true) return index
    }
    return -1
}

private fun split(text: String): List<String> {
    val result = mutableListOf<String>()
    var start = 0; var depth = 0; var quote: Char? = null
    for (index in text.indices) {
        val character = text[index]
        if (quote != null) { if (character == quote && text.getOrNull(index + 1) != quote) quote = null; continue }
        when (character) { '\'', '"' -> quote = character; '(' -> depth++; ')' -> depth--; ',' -> if (depth == 0) { result += text.substring(start, index).trim(); start = index + 1 } }
    }
    result += text.substring(start).trim()
    return result
}

private fun topLevelEquals(text: String): Int {
    var depth = 0; var quote: Char? = null
    for (index in text.indices) {
        val character = text[index]
        if (quote != null) { if (character == quote && text.getOrNull(index + 1) != quote) quote = null; continue }
        when (character) { '\'', '"' -> quote = character; '(' -> depth++; ')' -> depth--; '=' -> if (depth == 0) return index }
    }
    return -1
}

private fun splitTopLevelKeyword(text: String, keyword: String): List<String> {
    val result = mutableListOf<String>()
    var start = 0
    var depth = 0
    var quote: Char? = null
    var index = 0
    while (index <= text.length - keyword.length) {
        val character = text[index]
        if (quote != null) {
            if (character == quote) {
                if (text.getOrNull(index + 1) == quote) index++ else quote = null
            }
            index++
            continue
        }
        when (character) {
            '\'', '"' -> quote = character
            '(' -> depth++
            ')' -> depth--
        }
        if (depth == 0 && text.regionMatches(index, keyword, 0, keyword.length, ignoreCase = true) &&
            text.getOrNull(index - 1)?.isLetterOrDigit() != true && text.getOrNull(index + keyword.length)?.isLetterOrDigit() != true) {
            result += text.substring(start, index).trim()
            start = index + keyword.length
            index = start
            continue
        }
        index++
    }
    result += text.substring(start).trim()
    return result
}

private fun matchingParen(text: String, start: Int): Int {
    var depth = 0; var quote: Char? = null
    for (index in start until text.length) {
        val character = text[index]
        if (quote != null) { if (character == quote && text.getOrNull(index + 1) != quote) quote = null; continue }
        when (character) { '\'', '"' -> quote = character; '(' -> depth++; ')' -> if (--depth == 0) return index }
    }
    throw SqlException(SqlErrorCode.SYNTAX, "Unclosed parenthesis in VALUES")
}

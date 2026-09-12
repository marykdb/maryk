package io.maryk.cli.commands

import kotlinx.coroutines.runBlocking
import maryk.datastore.shared.rethrowIfFatal
import maryk.file.File
import maryk.sql.MarykSql
import maryk.sql.SqlConsistency
import maryk.sql.SqlException
import maryk.sql.SqlOptions
import maryk.sql.SqlReadOptions
import maryk.sql.SqlValue

class SqlCommand : Command {
    override val name = "sql"
    override val description = "Run a read-only SQL query on the connected store."
    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val connection = context.state.currentConnection ?: return error("Not connected to any store. Use `connect` first.")
        var consistency: SqlConsistency = SqlConsistency.Current
        var consistencySelected = false
        var includeDeleted = false
        var allowTableScan = true
        var statement: String? = null
        var index = 0
        while (index < arguments.size) {
            when (val token = arguments[index++]) {
                "--snapshot" -> {
                    if (consistencySelected) return error("Provide only one consistency option: --snapshot or --to-version")
                    consistencySelected = true
                    consistency = SqlConsistency.Snapshot
                }
                "--include-deleted" -> includeDeleted = true
                "--no-table-scan" -> allowTableScan = false
                "--to-version" -> {
                    if (consistencySelected) return error("Provide only one consistency option: --snapshot or --to-version")
                    consistencySelected = true
                    val version = arguments.getOrNull(index++)?.toULongOrNull() ?: return error("--to-version requires an unsigned version")
                    consistency = SqlConsistency.AtVersion(version)
                }
                "--file" -> {
                    if (statement != null) return error("Provide one SQL statement or one --file")
                    val path = arguments.getOrNull(index++) ?: return error("--file requires a path")
                    statement = try { File.readText(path, maxBytes = 64 * 1024) } catch (failure: Throwable) {
                        failure.rethrowIfFatal()
                        return error("Cannot read SQL file: ${failure.message}")
                    } ?: return error("Cannot read SQL file (maximum 64 KiB)")
                }
                "--" -> {
                    if (statement != null || arguments.size - index != 1) return error("Provide one SQL statement after --")
                    statement = arguments[index++]
                }
                else -> {
                    if (token.startsWith("--")) return error("Unknown SQL command option '$token'")
                    if (statement != null || index != arguments.size) return error("Pass the SQL text as one argument; the interactive `sql` command preserves it automatically")
                    statement = token
                }
            }
        }
        val sqlText = statement?.takeIf { it.isNotBlank() } ?: return CommandResult(
            listOf(
                "Usage: sql [options] SELECT ...",
                "       sql [options] --file <path>",
                "       sql [options] -- <SQL> (for SQL beginning with a -- comment)",
                "Options: --snapshot, --to-version <n>, --include-deleted, --no-table-scan",
                "Examples: sql SELECT * FROM Person LIMIT 20",
                "          sql SELECT category, COUNT(*) FROM Item GROUP BY category",
                "Read-only; maximum 10,000 result rows and 8 MiB of buffered results.",
            ), isError = true,
        )
        return try {
            val sql = MarykSql.create(connection.dataStore, options = SqlOptions(
                allowTableScan = allowTableScan,
                maxResultRows = 10_000,
                maxBufferedBytes = 8L * 1024 * 1024,
            ))
            val result = runBlocking { sql.query(sqlText, readOptions = SqlReadOptions(consistency, !includeDeleted)) }
            CommandResult(buildList {
                add(result.columns.joinToString("\t") { escaped(it.name) })
                result.rows.forEach { add(it.values.joinToString("\t", transform = ::formatCell)) }
            })
        } catch (failure: Throwable) {
            failure.rethrowIfFatal()
            val location = (failure as? SqlException)?.position?.let { " at character ${it + 1}" }.orEmpty()
            error("SQL failed$location: ${failure.message ?: failure::class.simpleName}")
        }
    }

    private fun error(message: String) = CommandResult(listOf(message), isError = true)

    private fun formatCell(value: SqlValue): String = when (value) {
        SqlValue.Null -> "NULL"
        is SqlValue.Text -> "\"${escaped(value.value)}\""
        is SqlValue.Bool -> value.value.toString()
        is SqlValue.Int64 -> value.value.toString()
        is SqlValue.UInt64 -> value.value.toString()
        is SqlValue.Exact -> value.value.toString()
        is SqlValue.Float64 -> value.value.toString()
        is SqlValue.Binary -> value.value.toString()
        is SqlValue.Date -> value.value.toString()
        is SqlValue.Time -> value.value.toString()
        is SqlValue.Timestamp -> value.value.toString()
        is SqlValue.Key -> value.value.toString()
        is SqlValue.Enum -> escaped(value.name)
    }

    private fun escaped(value: String): String = buildString {
        for (character in value) append(when (character) {
            '\\' -> "\\\\"
            '"' -> "\\\""
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> if (character.code < 32 || character.code == 127) "\\u${character.code.toString(16).padStart(4, '0')}" else character.toString()
        })
    }
}

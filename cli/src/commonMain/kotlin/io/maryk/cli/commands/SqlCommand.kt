package io.maryk.cli.commands

import kotlinx.coroutines.runBlocking
import maryk.datastore.shared.rethrowIfFatal
import maryk.file.File
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.shared.TransactionCancellationException
import maryk.sql.SqlSessionResult
import maryk.sql.SqlStatementKind
import maryk.sql.sqlStatementKind
import maryk.sql.session
import maryk.sql.MarykSql
import maryk.sql.SqlConsistency
import maryk.sql.SqlException
import maryk.sql.SqlOptions
import maryk.sql.SqlReadOptions
import maryk.sql.SqlValue

class SqlCommand : Command {
    override val name = "sql"
    override val description = "Run SQL queries or explicit --write mutations on the connected store."
    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        if ("--resolve-commit" in arguments) {
            if (arguments != listOf("--resolve-commit")) return error("--resolve-commit takes no SQL statement or other options")
            val pending = context.state.pendingSqlCommit ?: return error("No SQL commit is awaiting resolution in this session")
            val result = runBlocking { context.state.sqlSession?.takeIf { it.activeTransaction?.transaction === pending }?.resolveCommit() ?: pending.resolveCommit() }
            if (result.status != TransactionStatus.OutcomeUnknown) context.state.pendingSqlCommit = null
            return CommandResult(listOf("${result.status}: transaction ${pending.id}") + listOfNotNull(result.message),
                isError = result.status != TransactionStatus.Committed)
        }
        val connection = context.state.currentConnection ?: return error("Not connected to any store. Use `connect` first.")
        var consistency: SqlConsistency = SqlConsistency.Current
        var consistencySelected = false
        var includeDeleted = false
        var allowTableScan = context.state.sqlSessionOptions?.takeIf { context.state.sqlSession?.activeTransaction != null }?.allowTableScan ?: true
        var write = false
        var statement: String? = null
        var index = 0
        while (index < arguments.size) {
            when (val token = arguments[index++]) {
                "--write" -> write = true
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
                "Options: --write, --snapshot, --to-version <n>, --include-deleted, --no-table-scan",
                "Recovery: sql --resolve-commit resolves an uncertain write retained in this session.",
                "Examples: sql SELECT * FROM Person LIMIT 20",
                "          sql SELECT category, COUNT(*) FROM Item GROUP BY category",
                "Queries by default; --write enables bounded INSERT/UPDATE/DELETE/UNDELETE.",
                "Maximum 10,000 result rows and 8 MiB of buffered results.",
            ), isError = true,
        )
        if (write && (consistencySelected || includeDeleted)) return error("--write cannot be combined with snapshot, versioned-read, or include-deleted options")
        if (write && context.state.pendingSqlCommit != null) return error("Resolve the previous write with sql --resolve-commit before running another write")
        return try {
            val sql = MarykSql.create(connection.dataStore, options = SqlOptions(
                allowTableScan = allowTableScan,
                maxResultRows = 10_000,
                maxBufferedBytes = 8L * 1024 * 1024,
            ))
            if (context.state.sqlSession?.activeTransaction != null && context.state.sqlSessionOptions?.allowTableScan != allowTableScan) return error("Table-scan policy cannot change within an active SQL transaction")
            val kind = sqlStatementKind(sqlText)
            if (kind == SqlStatementKind.MUTATION && !write) return error("Use --write to run SQL mutations")
            if (kind == SqlStatementKind.QUERY && write) return error("Run SQL queries without --write")
            if (kind == SqlStatementKind.CONTROL) {
                if (!write) return error("Use --write to run SQL transaction commands")
                if (context.state.isOneShotMode) return error("Transaction commands require an interactive CLI connection")
                val session = context.state.sqlSession?.takeIf { it.activeTransaction != null } ?: sql.session().also { context.state.sqlSession = it; context.state.sqlSessionOptions = sql.options }
                val control = runBlocking { session.execute(sqlText) } as SqlSessionResult.Control
                val commit = control.commit
                if (commit?.status == TransactionStatus.OutcomeUnknown) context.state.pendingSqlCommit = session.activeTransaction?.transaction
                return CommandResult(listOf(control.command + (commit?.let { ": ${it.status}" } ?: "")),
                    isError = commit != null && commit.status != TransactionStatus.Committed)
            }
            if (write) {
                val result = runBlocking { context.state.sqlSession?.takeIf { it.activeTransaction != null }?.let {
                    (it.execute(sqlText) as SqlSessionResult.Mutation).result
                } ?: sql.execute(sqlText) }
                if (result.response.status == AtomicStatus.OutcomeUnknown) context.state.pendingSqlCommit = result.transaction
                return CommandResult(
                    buildList {
                        add(if (result.response.status == AtomicStatus.OutcomeUnknown) "OutcomeUnknown: affected rows unconfirmed"
                            else "${result.response.status}: ${result.affectedRows} affected rows")
                        result.transaction?.takeIf { result.response.status == AtomicStatus.OutcomeUnknown }?.let {
                            add("Transaction ${it.id}. Use sql --resolve-commit in this session; do not rerun the write.")
                        }
                        result.response.message?.let { add(escaped(it)) }
                        result.response.failure?.let { add(escaped(it.toString())) }
                        result.returning.forEach { returned ->
                            if (returned.columns.isNotEmpty()) add(returned.columns.joinToString("\t") { escaped(it.name) })
                            returned.rows.forEach { add(it.values.joinToString("\t", transform = ::formatCell)) }
                        }
                    },
                    isError = result.response.status != AtomicStatus.Committed && result.response.status != AtomicStatus.Staged,
                )
            }
            val result = runBlocking { context.state.sqlSession?.takeIf { it.activeTransaction != null }?.let {
                (it.execute(sqlText, readOptions = SqlReadOptions(consistency, !includeDeleted)) as SqlSessionResult.Query).result
            } ?: sql.query(sqlText, readOptions = SqlReadOptions(consistency, !includeDeleted)) }
            CommandResult(buildList {
                add(result.columns.joinToString("\t") { escaped(it.name) })
                result.rows.forEach { add(it.values.joinToString("\t", transform = ::formatCell)) }
            })
        } catch (failure: Throwable) {
            if (failure is TransactionCancellationException) context.state.pendingSqlCommit = failure.transaction
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
        is SqlValue.Collection -> escaped(value.toString())
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

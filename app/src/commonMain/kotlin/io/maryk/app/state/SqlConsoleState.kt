package io.maryk.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.types.Decimal
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.rethrowIfFatal
import maryk.sql.MarykSql
import maryk.sql.SqlCatalog
import maryk.sql.SqlColumn
import maryk.sql.SqlConsistency
import maryk.sql.SqlErrorCode
import maryk.sql.SqlException
import maryk.sql.SqlExecutionSummary
import maryk.sql.SqlOptions
import maryk.sql.SqlReadOptions
import maryk.sql.SqlRow
import maryk.sql.SqlType
import maryk.sql.SqlValue

internal enum class SqlParameterKind { TEXT, INTEGER, UNSIGNED, DECIMAL, FLOAT, BOOLEAN, DATE, TIME, TIMESTAMP, NULL }

internal data class SqlParameterInput(val kind: SqlParameterKind = SqlParameterKind.TEXT, val text: String = "") {
    fun value(): SqlValue = when (kind) {
        SqlParameterKind.TEXT -> SqlValue.Text(text)
        SqlParameterKind.INTEGER -> SqlValue.Int64(text.toLong())
        SqlParameterKind.UNSIGNED -> SqlValue.UInt64(text.toULong())
        SqlParameterKind.DECIMAL -> SqlValue.Exact(Decimal.parse(text))
        SqlParameterKind.FLOAT -> SqlValue.Float64(text.toDouble())
        SqlParameterKind.BOOLEAN -> SqlValue.Bool(when (text.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Use true or false")
        })
        SqlParameterKind.DATE -> SqlValue.Date(LocalDate.parse(text))
        SqlParameterKind.TIME -> SqlValue.Time(LocalTime.parse(text))
        SqlParameterKind.TIMESTAMP -> SqlValue.Timestamp(LocalDateTime.parse(text.replace(' ', 'T')))
        SqlParameterKind.NULL -> SqlValue.Null
    }
}

/** One console per browser connection. Reopening the modal retains the draft and results. */
class SqlConsoleState(private val scope: CoroutineScope) {
    var editor by mutableStateOf(TextFieldValue("SELECT 1"))
    internal var parameters by mutableStateOf<List<SqlParameterInput>>(emptyList())
    var writeMode by mutableStateOf(false)
    var snapshot by mutableStateOf(false)
    var includeDeleted by mutableStateOf(false)
    var running by mutableStateOf(false)
        private set
    var columns by mutableStateOf<List<SqlColumn>>(emptyList())
        private set
    var rows by mutableStateOf<List<SqlRow>>(emptyList())
        private set
    var summary by mutableStateOf<SqlExecutionSummary?>(null)
        private set
    var message by mutableStateOf("Run a query against the connected store.")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var errorPosition by mutableStateOf<Int?>(null)
        private set
    var catalog by mutableStateOf<SqlCatalog?>(null)
        private set
    var supportsSnapshot by mutableStateOf(false)
        private set
    private var sql: MarykSql? = null
    private var job: Job? = null
    private var generation = 0

    fun attach(store: IsDataStore) {
        detach()
        try {
            val facade = MarykSql.create(store, options = SqlOptions(
                allowTableScan = true, maxResultRows = 10_000, maxBufferedBytes = RESULT_BYTES,
            ))
            sql = facade
            catalog = facade.catalog
            supportsSnapshot = facade.capabilities.historicalReads
            message = "Ready. Results are limited to 10,000 rows and 4 MiB."
        } catch (failure: Exception) {
            failure.rethrowIfFatal()
            error = failure.message ?: "Could not load the SQL catalog"
        }
    }

    fun detach() {
        generation++
        job?.cancel()
        job = null
        sql = null
        catalog = null
        running = false
        columns = emptyList()
        rows = emptyList()
        summary = null
        error = null
        errorPosition = null
        writeMode = false
        snapshot = false
        includeDeleted = false
        supportsSnapshot = false
        message = "Connect a store to run SQL."
    }

    internal fun updateParameter(index: Int, input: SqlParameterInput) {
        parameters = parameters.toMutableList().also { while (it.size <= index) it += SqlParameterInput(); it[index] = input }
    }

    fun updateEditor(value: TextFieldValue) {
        if (value.text != editor.text) {
            error = null
            errorPosition = null
        }
        editor = value
    }

    internal fun initializeParameters(count: Int) {
        val inferred = try { sql?.prepare(editor.text)?.parameters.orEmpty() } catch (_: IllegalArgumentException) { emptyList() }
        parameters = List(count) { index -> parameters.getOrNull(index) ?: SqlParameterInput(when (inferred.getOrNull(index)?.type) {
            SqlType.BOOLEAN -> SqlParameterKind.BOOLEAN
            SqlType.INT64 -> SqlParameterKind.INTEGER
            SqlType.UINT64 -> SqlParameterKind.UNSIGNED
            SqlType.DECIMAL -> SqlParameterKind.DECIMAL
            SqlType.FLOAT64 -> SqlParameterKind.FLOAT
            SqlType.DATE -> SqlParameterKind.DATE
            SqlType.TIME -> SqlParameterKind.TIME
            SqlType.TIMESTAMP -> SqlParameterKind.TIMESTAMP
            else -> SqlParameterKind.TEXT
        }) }
    }

    fun selectError() {
        errorPosition?.coerceIn(0, editor.text.length)?.let { position ->
            editor = editor.copy(selection = TextRange(position, (position + 1).coerceAtMost(editor.text.length)))
        }
    }

    fun cancel() { job?.cancel() }

    fun run(parameterCount: Int, explain: Boolean = false, atVersion: ULong? = null) {
        if (running) return
        val facade = sql ?: return
        val statement = editor.text
        val writing = writeMode
        if (writing && (explain || snapshot || includeDeleted || atVersion != null)) {
            error = "Turn off writes to use Explain, time travel, snapshots, or include deleted."
            return
        }
        val values = try { List(parameterCount) { index ->
            try { parameters.getOrElse(index) { SqlParameterInput() }.value() }
            catch (failure: IllegalArgumentException) { throw IllegalArgumentException("Parameter ${index + 1}: ${failure.message}") }
        } } catch (failure: IllegalArgumentException) { error = failure.message; return }
        val readOptions = SqlReadOptions(
            consistency = atVersion?.let(SqlConsistency::AtVersion) ?: if (snapshot) SqlConsistency.Snapshot else SqlConsistency.Current,
            filterSoftDeleted = !includeDeleted,
        )
        val ticket = ++generation
        running = true
        error = null
        errorPosition = null
        rows = emptyList()
        columns = emptyList()
        summary = null
        message = if (writing) "Writing…" else "Running…"
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val collected = mutableListOf<SqlRow>()
            var resultColumns = emptyList<SqlColumn>()
            var resultSummary: SqlExecutionSummary? = null
            var resultMessage = "Completed"
            var resultError: String? = null
            var position: Int? = null
            try {
                withContext(Dispatchers.Default) {
                    if (writing) {
                        val result = facade.execute(statement, values)
                        resultMessage = "Affected rows: ${result.affectedRows}"
                        resultError = result.failures.takeIf { it.isNotEmpty() }?.joinToString("\n")
                    } else {
                        val prepared = facade.prepare(statement)
                        if (explain) {
                            resultMessage = prepared.explain()
                        } else {
                            resultColumns = prepared.columns
                            val execution = prepared.execute(values, readOptions)
                            var bytes = 0L
                            try {
                                execution.rows.collect { row ->
                                    val size = 64L + row.values.sumOf { 32L + sqlCellText(it).length * 2L }
                                    if (size > RESULT_BYTES - bytes) throw SqlException(SqlErrorCode.LIMIT, "Displayed results exceed 4 MiB")
                                    bytes += size
                                    collected += row
                                }
                            } finally { resultSummary = execution.awaitCompletion() }
                            resultMessage = "${collected.size} rows"
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                resultMessage = "Cancelled. ${collected.size} partial rows."
                if (writing) resultMessage = "Write cancelled. Check the store before retrying; cancellation does not roll back writes."
                throw cancelled
            } catch (failure: Exception) {
                failure.rethrowIfFatal()
                resultError = failure.message ?: "SQL failed"
                position = (failure as? SqlException)?.position
                resultMessage = "Failed. ${collected.size} partial rows."
            } finally {
                if (generation == ticket) {
                    rows = collected.toList()
                    columns = resultColumns
                    summary = resultSummary
                    message = resultMessage
                    error = resultError
                    errorPosition = position
                    running = false
                    job = null
                }
            }
        }
    }

    companion object { private const val RESULT_BYTES = 4L * 1024 * 1024 }
}

internal fun sqlCellText(value: SqlValue): String = when (value) {
    SqlValue.Null -> "NULL"
    is SqlValue.Text -> "'${value.value.replace("'", "''")}'"
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
    is SqlValue.Enum -> value.name
}

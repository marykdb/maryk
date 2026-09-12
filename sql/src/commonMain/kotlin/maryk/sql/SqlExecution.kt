package maryk.sql

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

enum class SqlExecutionStatus { SUCCEEDED, CANCELLED, FAILED }

data class SqlExecutionSummary(
    val status: SqlExecutionStatus,
    val fetchedRows: Long,
    val returnedRows: Long,
    val storeRequests: Int,
    val expressionSteps: Long,
    /** Conservative logical accounting, not the platform allocator's heap usage. */
    val peakBufferedBytes: Long,
    val snapshotVersion: ULong?,
    val errorCode: SqlErrorCode? = null,
    val errorMessage: String? = null,
)

/** One cold, single-collector execution. Cancelling never closes the caller-owned data store. */
class SqlExecution internal constructor(
    val columns: List<SqlColumn>,
    options: SqlOptions,
    private val operation: suspend (SqlBudget, suspend (SqlRow) -> Unit) -> Unit,
) {
    private val claimed = MutableStateFlow(false)
    private val cancellation = MutableStateFlow(false)
    private val job = MutableStateFlow<Job?>(null)
    private val completion = CompletableDeferred<SqlExecutionSummary>()
    private val budget = SqlBudget(options)

    internal fun retainCollectedRow(row: SqlRow) = budget.retain(64L + row.values.sumOf(::valueBytes))

    val rows: Flow<SqlRow> = flow {
        if (!claimed.compareAndSet(false, true)) throw SqlException(SqlErrorCode.EXECUTION_STATE, "An execution's rows can only be collected once; execute the prepared query again")
        var failure: Throwable? = null
        try {
            coroutineScope {
                job.value = currentCoroutineContext()[Job]
                if (cancellation.value) throw CancellationException("SQL execution cancelled")
                withTimeout(options.maxExecutionMillis) {
                    operation(budget) { emit(it) }
                }
            }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            job.value = null
            completion.complete(summary(failure))
        }
    }

    fun cancel() {
        cancellation.value = true
        if (claimed.compareAndSet(false, true)) {
            completion.complete(summary(CancellationException("SQL execution cancelled before collection")))
        } else job.value?.cancel(CancellationException("SQL execution cancelled"))
    }

    /** Completion follows cleanup. Calling before collection/cancellation is a usage error. */
    suspend fun awaitCompletion(): SqlExecutionSummary {
        if (!claimed.value) throw SqlException(SqlErrorCode.EXECUTION_STATE, "Collect rows (or cancel) before awaiting completion")
        return completion.await()
    }

    private fun summary(error: Throwable?): SqlExecutionSummary = SqlExecutionSummary(
        status = when (error) { null -> SqlExecutionStatus.SUCCEEDED; is CancellationException -> SqlExecutionStatus.CANCELLED; else -> SqlExecutionStatus.FAILED },
        fetchedRows = budget.fetched,
        returnedRows = budget.returned,
        storeRequests = budget.requests,
        expressionSteps = budget.steps,
        peakBufferedBytes = budget.peakBytes,
        snapshotVersion = budget.snapshotVersion,
        errorCode = (error as? SqlException)?.code,
        errorMessage = error?.message,
    )
}

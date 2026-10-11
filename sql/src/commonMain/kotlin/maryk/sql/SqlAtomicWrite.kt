package maryk.sql

import kotlinx.coroutines.withTimeoutOrNull
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.MAX_ATOMIC_OPERATIONS
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.Transaction
import maryk.datastore.shared.asAtomicResponse
import maryk.datastore.shared.atomicCapabilities
import maryk.datastore.shared.currentTransaction
import maryk.datastore.shared.executeAtomic
import maryk.datastore.shared.executeTransaction
import maryk.datastore.shared.supportsAtomicRequests
import maryk.sql.syntax.LiteralKind
import maryk.sql.syntax.SqlExpr
import maryk.sql.syntax.SqlMutation
import maryk.sql.syntax.SqlMutationKind
import maryk.sql.syntax.SqlName
import maryk.sql.syntax.SqlParseException
import maryk.sql.syntax.SqlParser
import maryk.sql.syntax.SqlQuery
import maryk.sql.syntax.SqlSelect
import maryk.sql.syntax.SqlTableSource

/** One independently parameterized statement in a prepared atomic change set. */
data class SqlWriteStatement(val statement: String, val parameters: List<SqlValue> = emptyList())

/** Returning results correspond to input statements. Staged results contain provisional versions. */
data class SqlAtomicWriteResult(
    val response: AtomicResponse,
    val affectedRows: Int,
    val returning: List<SqlResult>,
    val transaction: Transaction? = null,
)

/** Whether the backend can commit a SQL change set without partial writes. */
data class SqlAtomicCapabilities(val store: AtomicCapabilities, val maxStatements: UInt = MAX_ATOMIC_OPERATIONS)

val MarykSql.atomicWriteCapabilities: SqlAtomicCapabilities?
    get() = dataStore.atomicCapabilities?.let { SqlAtomicCapabilities(it) }

val MarykSql.supportsAtomicWrites: Boolean get() = dataStore.supportsAtomicRequests

/**
 * A prepared, bounded write set with stable keys and version guards. Execute this same object
 * again after an ambiguous outcome. Statements are planned against stored data, not each
 * other's staged results; a model/key may be mutated only once in the whole change set.
 */
class PreparedSqlAtomicWrite internal constructor(
    private val store: IsDataStore,
    val request: AtomicRequest?,
    private val projections: List<AtomicReturning>,
    private val rows: List<AtomicReturningRow>,
    private val transactionRequest: TransactionRequest? = null,
    private val executionMillis: Long = 30_000,
) {
    suspend fun execute(): SqlAtomicWriteResult {
        val ambient = currentTransaction(store)
        if (ambient?.readOnly == true || (store as? SqlTransactionStore)?.readOnly == true) {
            throw SqlException(SqlErrorCode.EXECUTION_STATE, "Read-only transactions cannot execute SQL mutations")
        }
        val response = if (transactionRequest != null) {
            val staged = withTimeoutOrNull(executionMillis) { store.executeTransaction(transactionRequest) }
                ?: throw SqlLimitException("executionMillis", executionMillis.toULong())
            staged.asAtomicResponse()
        }
        else if (request != null) store.executeAtomic(request)
        else if (ambient != null) ambient.executeBatch(emptyList()).asAtomicResponse()
        else if (store is SqlTransactionStore) store.executeTransaction(TransactionRequest("sql-noop")).asAtomicResponse()
        else AtomicResponse(if (store.supportsAtomicRequests) AtomicStatus.Committed else AtomicStatus.Unsupported)
        return result(response)
    }

    internal fun result(response: AtomicResponse): SqlAtomicWriteResult {
        val committed = response.status == AtomicStatus.Committed || response.status == AtomicStatus.Staged
        val resultRows = List(projections.size) { mutableListOf<SqlRow>() }
        if (committed) rows.forEachIndexed { index, row ->
            val projection = projections[row.statement]
            if (projection.columns.isNotEmpty()) {
                val version = when (val member = response.responses[index]) {
                    is AddResponse<*> -> (member.statuses.single() as AddSuccess<*>).version
                    is ChangeResponse<*> -> (member.statuses.single() as ChangeSuccess<*>).version
                    is DeleteResponse<*> -> (member.statuses.single() as DeleteSuccess<*>).version
                    else -> throw SqlException(SqlErrorCode.EXECUTION_STATE, "Unexpected atomic member response")
                }
                val values = row.values.mapIndexed { column, value ->
                    if (projection.bindings[column].isVersion) SqlValue.UInt64(version) else value
                }
                resultRows[row.statement] += SqlRow(projection.columns, values)
            }
        }
        return SqlAtomicWriteResult(response, if (committed) rows.size else 0,
            projections.mapIndexed { index, projection -> SqlResult(projection.columns, resultRows[index].toList()) })
    }
}

internal data class AtomicReturning(val columns: List<SqlColumn>, val bindings: List<CatalogColumn>, val selections: List<SqlSelect>)
internal data class AtomicReturningRow(val statement: Int, val values: List<SqlValue>)

internal class SqlAtomicWriter(private val sql: MarykSql) {
    private val writer = SqlWriter(sql)
    private val budget = writer.budget
    private val members = mutableListOf<IsStoreRequest<*, *>>()
    private val returningRows = mutableListOf<AtomicReturningRow>()

    suspend fun prepare(changeSetId: String, statements: List<SqlWriteStatement>, transactional: Boolean = false): PreparedSqlAtomicWrite {
        if ((sql.dataStore as? SqlTransactionStore)?.readOnly == true || currentTransaction(sql.dataStore)?.readOnly == true) {
            throw SqlException(SqlErrorCode.EXECUTION_STATE, "Read-only transactions cannot execute SQL mutations")
        }
        if (!sql.supportsAtomicWrites) throw SqlException(SqlErrorCode.UNSUPPORTED, "This datastore does not support atomic SQL writes")
        if (changeSetId.isBlank() || changeSetId.length > 128) throw SqlException(SqlErrorCode.PARAMETER, "changeSetId must contain 1 to 128 characters")
        if (statements.isEmpty()) throw SqlException(SqlErrorCode.PARAMETER, "Atomic SQL requires at least one statement")
        if (statements.size > MAX_ATOMIC_OPERATIONS.toInt()) budget.exceeded("statements", MAX_ATOMIC_OPERATIONS.toLong(), statements.size.toLong())
        return withTimeoutOrNull(sql.options.maxExecutionMillis) {
            val parsed = statements.map { statement ->
                val mutation = try { SqlParser(statement.statement).parseMutation() }
                catch (error: SqlParseException) { throw error.toSqlException() }
                if (!transactional && mutation.kind == SqlMutationKind.HARD_DELETE) throw SqlException(SqlErrorCode.UNSUPPORTED, "Atomic SQL does not support hard deletion")
                mutation
            }
            val projections = parsed.mapIndexed { index, mutation ->
                budget.checkpoint()
                val table = writer.table(mutation.table)
                val projection = returning(table, mutation)
                if (mutation.kind == SqlMutationKind.INSERT) insert(index, table, mutation, statements[index].parameters, projection)
                else mutate(index, table, mutation, statements[index].parameters, projection)
                projection
            }
            budget.checkpoint()
            // Account for the eventual commit in the same request budget as planning reads.
            if (members.isNotEmpty()) budget.request()
            val request = try { if (transactional || members.isEmpty()) null else AtomicRequest(changeSetId, members) }
            catch (error: RequestException) { throw SqlException(SqlErrorCode.UNSUPPORTED, error.message ?: "Invalid atomic change set", cause = error) }
            PreparedSqlAtomicWrite(sql.dataStore, request, projections, returningRows.toList(),
                if (transactional) TransactionRequest(changeSetId, members.toList(), commit = true) else null, sql.options.maxExecutionMillis)
        } ?: budget.exceeded("planningMillis", sql.options.maxExecutionMillis)
    }

    private fun returning(table: SqlTable, mutation: SqlMutation): AtomicReturning {
        if (mutation.returning.isEmpty()) return AtomicReturning(emptyList(), emptyList(), emptyList())
        if (mutation.returning.any { it.expression !is SqlExpr.Column && it.expression !is SqlExpr.Star }) {
            throw SqlException(SqlErrorCode.UNSUPPORTED, "RETURNING supports scalar columns and wildcards, with optional aliases")
        }
        val plan = sql.prepare(query(mutation.returning, mutation.parameterCount).copy(from = source(table))).plan
        val bindings = plan.query.select.map { selection -> plan.references[selection.expression as SqlExpr.Column]!! }
        return AtomicReturning(plan.columns, bindings, plan.query.select)
    }

    private suspend fun insert(index: Int, table: SqlTable, mutation: SqlMutation, parameters: List<SqlValue>, projection: AtomicReturning) {
        val columns = writer.columns(table, mutation.columns)
        val values = if (mutation.sourceQuery != null) {
            val source = sql.prepare(mutation.sourceQuery)
            if (source.columns.size != columns.size) throw SqlException(SqlErrorCode.SYNTAX, "INSERT query width does not match column list")
            writer.evaluate(mutation.sourceQuery, parameters)
        } else {
            if (mutation.rows.any { it.size != columns.size }) throw SqlException(SqlErrorCode.SYNTAX, "INSERT VALUES width does not match column list")
            mutation.rows.flatten().forEach { expression ->
                if (containsColumn(expression) || containsAggregate(expression) || containsSubquery(expression)) {
                    throw SqlException(SqlErrorCode.UNSUPPORTED, "INSERT VALUES requires scalar constant expressions", expression.position)
                }
            }
            ensureCount(mutation.rows.size)
            val evaluated = writer.evaluate(query(mutation.rows.flatten().map { SqlSelect(it, null) }, mutation.parameterCount), parameters).single()
            mutation.rows.indices.map { row -> evaluated.subList(row * columns.size, (row + 1) * columns.size) }
        }
        ensureCount(values.size)
        for (row in values) {
            budget.step()
            val pairs = columns.mapIndexedNotNull { column, definition ->
                val value = row[column]
                if (value == SqlValue.Null) { writer.nativeValue(definition, value); null } else writer.pair(definition, value)
            }
            val record = table.model.fromChanges(null, listOf(Change(*pairs.toTypedArray())))
            val key = table.model.key(record)
            val returned = projection.bindings.map { it.read(ValuesWithMetaData(key, record, 0uL, 0uL, false)) }
            add(index, table.model.add(key to record), returned)
        }
    }

    private suspend fun mutate(index: Int, table: SqlTable, mutation: SqlMutation, parameters: List<SqlValue>, projection: AtomicReturning) {
        val assignments = writer.columns(table, mutation.assignments.map { it.first })
        mutation.assignments.forEach { (_, expression) ->
            if (containsAggregate(expression) || containsSubquery(expression)) throw SqlException(SqlErrorCode.UNSUPPORTED, "UPDATE assignments require scalar expressions", expression.position)
        }
        val selection = listOf(system("__key"), system("__version")) + mutation.assignments.map { SqlSelect(it.second, null) } + projection.selections
        val query = query(selection, mutation.parameterCount).copy(from = source(table), where = mutation.where,
            limit = SqlExpr.Literal((remainingMutations() + 1).toString(), LiteralKind.NUMBER, 0))
        val rows = writer.evaluate(query, parameters, SqlReadOptions(filterSoftDeleted = mutation.kind != SqlMutationKind.UNDELETE && mutation.kind != SqlMutationKind.HARD_DELETE))
        ensureCount(rows.size)
        for (row in rows) {
            budget.step()
            val key = Key<IsRootDataModel>((row[0] as SqlValue.Key).value.bytes)
            val version = (row[1] as SqlValue.UInt64).value
            val pairs = assignments.mapIndexed { column, definition -> writer.pair(definition, row[column + 2]) }
            val member = if (mutation.kind == SqlMutationKind.DELETE || mutation.kind == SqlMutationKind.HARD_DELETE) table.model.delete(key, hardDelete = mutation.kind == SqlMutationKind.HARD_DELETE, lastVersion = version)
            else table.model.change(key.change(
                if (mutation.kind == SqlMutationKind.UNDELETE) ObjectSoftDeleteChange(false) else Change(*pairs.toTypedArray()),
                lastVersion = version,
            ))
            val returned = projection.bindings.mapIndexed { column, definition ->
                val assignment = assignments.indexOfFirst { it.indices == definition.indices }
                if (mutation.kind == SqlMutationKind.UPDATE && assignment >= 0) definition.fromNative(writer.nativeValue(assignments[assignment], row[assignment + 2]))
                else row[2 + assignments.size + column]
            }
            add(index, member, returned)
        }
    }

    private fun remainingMutations(): Long = minOf(MAX_ATOMIC_OPERATIONS.toLong(), sql.dataStore.atomicCapabilities!!.maxMutations.toLong(), sql.options.maxResultRows) - members.size

    private fun ensureCount(count: Int) {
        if (count > remainingMutations()) {
            budget.exceeded("mutations", minOf(MAX_ATOMIC_OPERATIONS.toLong(), sql.dataStore.atomicCapabilities!!.maxMutations.toLong(), sql.options.maxResultRows), count.toLong() + members.size)
        }
    }

    private fun add(index: Int, member: IsStoreRequest<*, *>, returned: List<SqlValue>) {
        budget.retain(128L + returned.sumOf(::valueBytes))
        members += member
        returningRows += AtomicReturningRow(index, returned)
    }

    private fun source(table: SqlTable) = SqlTableSource(listOf(SqlName(table.schema, true), SqlName(table.name, true)), null)
    private fun system(name: String) = SqlSelect(SqlExpr.Column(listOf(SqlName(name)), 0), null)
    private fun query(selection: List<SqlSelect>, parameterCount: Int) = SqlQuery(
        select = selection, from = null, where = null, groupBy = emptyList(), having = null, orderBy = emptyList(),
        limit = null, offset = null, distinct = false, parameterCount = parameterCount,
    )
}

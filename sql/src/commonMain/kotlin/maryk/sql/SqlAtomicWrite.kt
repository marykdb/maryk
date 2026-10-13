package maryk.sql

import kotlinx.coroutines.withTimeoutOrNull
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
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
import maryk.core.query.requests.get
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
import maryk.sql.syntax.mapColumns
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
    get() = dataStore.atomicCapabilities?.let(::SqlAtomicCapabilities)

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
                    if (projection.bindings[column]?.isVersion == true) SqlValue.UInt64(version) else value
                }
                resultRows[row.statement] += SqlRow(projection.columns, values)
            }
        }
        return SqlAtomicWriteResult(response, if (committed) rows.size else 0,
            projections.mapIndexed { index, projection -> SqlResult(projection.columns, resultRows[index].toList()) })
    }
}

internal data class AtomicReturning(val columns: List<SqlColumn>, val bindings: List<CatalogColumn?>, val selections: List<SqlSelect>, val plan: BoundQuery? = null, val inputs: List<CatalogColumn> = emptyList())
internal data class AtomicReturningRow(val statement: Int, val values: List<SqlValue>)

internal class SqlAtomicWriter(private val sql: MarykSql) {
    private val writer = SqlWriter(sql)
    private val budget = writer.budget
    private val members = mutableListOf<IsStoreRequest<*, *>>()
    private val returningRows = mutableListOf<AtomicReturningRow>()

    suspend fun prepare(changeSetId: String, statements: List<SqlWriteStatement>, transactional: Boolean = false, syntax: List<SqlMutation>? = null): PreparedSqlAtomicWrite {
        if ((sql.dataStore as? SqlTransactionStore)?.readOnly == true || currentTransaction(sql.dataStore)?.readOnly == true) {
            throw SqlException(SqlErrorCode.EXECUTION_STATE, "Read-only transactions cannot execute SQL mutations")
        }
        if (!sql.supportsAtomicWrites) throw SqlException(SqlErrorCode.UNSUPPORTED, "This datastore does not support atomic SQL writes")
        if (changeSetId.isBlank() || changeSetId.length > 128) throw SqlException(SqlErrorCode.PARAMETER, "changeSetId must contain 1 to 128 characters")
        if (statements.isEmpty()) throw SqlException(SqlErrorCode.PARAMETER, "Atomic SQL requires at least one statement")
        if (statements.size > MAX_ATOMIC_OPERATIONS.toInt()) budget.exceeded("statements", MAX_ATOMIC_OPERATIONS.toLong(), statements.size.toLong())
        return withTimeoutOrNull(sql.options.maxExecutionMillis) {
            val parsed = statements.mapIndexed { index, statement ->
                val mutation = syntax?.get(index) ?: try { SqlParser(statement.statement).parseMutation() }
                catch (error: SqlParseException) { throw error.toSqlException() }
                writer.validateExpressions(mutation)
                if (!transactional && mutation.conflict != null) throw SqlException(SqlErrorCode.UNSUPPORTED, "ON CONFLICT requires serializable SQL execution")
                if (!transactional && mutation.joinedSource != null) throw SqlException(SqlErrorCode.UNSUPPORTED, "Joined mutations require serializable SQL execution")
                if (!transactional && mutation.kind == SqlMutationKind.HARD_DELETE) throw SqlException(SqlErrorCode.UNSUPPORTED, "Atomic SQL does not support hard deletion")
                mutation
            }
            val projections = parsed.mapIndexed { index, mutation ->
                budget.checkpoint()
                val table = writer.table(mutation.table)
                val projection = bindReturning(table, mutation)
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

    internal fun bindReturning(table: SqlTable, mutation: SqlMutation): AtomicReturning {
        if (mutation.returning.isEmpty()) return AtomicReturning(emptyList(), emptyList(), emptyList())
        val plan = sql.prepare(query(mutation.returning, mutation.parameterCount).copy(from = writer.mutationSource(table, mutation, joined = false))).plan
        val bindings = plan.query.select.map { selection -> (selection.expression as? SqlExpr.Column)?.let { plan.references[it] } }
        for (selection in plan.query.select) {
            fun usesVersion(expression: SqlExpr): Boolean = expression is SqlExpr.Column && plan.references[expression]?.isVersion == true || children(expression).any(::usesVersion)
            if (selection.expression !is SqlExpr.Column && usesVersion(selection.expression)) throw SqlException(SqlErrorCode.UNSUPPORTED, "RETURNING __version must be a direct column because its durable value is assigned at commit")
        }
        val inputs = plan.inputs
        val selections = inputs.map { binding -> SqlSelect(plan.references.entries.first { it.value == binding }.key, null) }
        return AtomicReturning(plan.columns, bindings, selections, plan, inputs)
    }

    private suspend fun insert(index: Int, table: SqlTable, mutation: SqlMutation, parameters: List<SqlValue>, projection: AtomicReturning) {
        val columns = writer.columns(table, mutation.columns)
        val conflictColumns = mutation.conflict?.let { writer.conflictColumns(table, it) }
        val conflicts = mutableSetOf<List<Any>>()
        val values = if (mutation.sourceQuery != null) {
            val source = sql.prepare(mutation.sourceQuery)
            if (source.columns.size != columns.size) throw SqlException(SqlErrorCode.SYNTAX, "INSERT query width does not match column list")
            writer.evaluate(mutation.sourceQuery, parameters)
        } else {
            if (mutation.rows.any { it.size != columns.size }) throw SqlException(SqlErrorCode.SYNTAX, "INSERT VALUES width does not match column list")
            ensureCount(mutation.rows.size)
            val evaluated = writer.evaluate(query(mutation.rows.flatten().map { SqlSelect(it, null) }, mutation.parameterCount), parameters).single()
            mutation.rows.indices.map { row -> evaluated.subList(row * columns.size, (row + 1) * columns.size) }
        }
        ensureCount(values.size)
        for (row in values) {
            budget.step()
            val record = writer.insertRecord(table, columns, row)
            val key = table.model.key(record)
            if (conflictColumns != null) {
                val conflictValues = conflictColumns.map { it.read(ValuesWithMetaData(key, record, 0uL, 0uL, false)) }
                if (conflictValues.none { it == SqlValue.Null }) {
                    val identity = conflictValues.map(::sqlKey)
                    if (!conflicts.add(identity)) {
                        if (mutation.conflict.assignments.isEmpty()) continue
                        throw SqlException(SqlErrorCode.TYPE, "ON CONFLICT DO UPDATE cannot target one record twice within a statement")
                    }
                    val extra = parameters + conflictValues
                    val predicates = conflictColumns.mapIndexed { i, binding -> SqlExpr.Binary("=", SqlExpr.Column(listOf(SqlName(binding.column.name, true)), 0), SqlExpr.Parameter(parameters.size + i, 0), 0) }
                    val predicate = predicates.reduce { a, b -> SqlExpr.Binary("AND", a, b, 0) }
                    val match = writer.evaluate(query(listOf(system("__key")), extra.size).copy(from = source(table), where = predicate, limit = SqlExpr.Literal("2", LiteralKind.NUMBER, 0)), extra)
                    if (match.size > 1) throw SqlException(SqlErrorCode.TYPE, "Conflict target matched more than one record")
                    if (match.isNotEmpty()) {
                        if (mutation.conflict.assignments.isEmpty()) continue
                        val excludedColumns = table.bindings.filter { it.column.type != SqlType.ANY }
                        val excludedValues = excludedColumns.map { it.read(ValuesWithMetaData(key, record, 0uL, 0uL, false)) }
                        val boundValues = parameters + excludedValues + match.single().single()
                        val assignments = mutation.conflict.assignments.map { (name, expression) -> name to expression.mapColumns { column ->
                            if (column.path.first().normalized != "excluded") column else {
                                val path = column.path.drop(1).joinToString(".") { it.text }
                                val input = excludedColumns.indexOfFirst { it.column.name == path || asciiLower(it.column.name) == asciiLower(path) && column.path.drop(1).none { it.quoted } }
                                if (input < 0) throw SqlException(SqlErrorCode.BINDING, "Unknown excluded column '$path'", column.position)
                                SqlExpr.Parameter(parameters.size + input, column.position)
                            }
                        } }
                        val target = SqlExpr.Binary("=", SqlExpr.Column(listOf(SqlName("__key")), 0), SqlExpr.Parameter(boundValues.lastIndex, 0), 0)
                        mutate(index, table, mutation.copy(kind = SqlMutationKind.UPDATE, assignments = assignments, where = target, parameterCount = boundValues.size), boundValues, projection)
                        continue
                    }
                }
            }
            val input = projection.inputs.associateWith { it.read(ValuesWithMetaData(key, record, 0uL, 0uL, false)) }
            add(index, table.model.add(key to record), returned(projection, parameters, input))
        }
    }

    private suspend fun mutate(index: Int, table: SqlTable, mutation: SqlMutation, parameters: List<SqlValue>, projection: AtomicReturning) {
        val assignments = writer.columns(table, mutation.assignments.map { it.first })
        val qualifier = mutation.alias ?: mutation.table.last()
        fun target(name: String) = SqlSelect(SqlExpr.Column(listOf(qualifier, SqlName(name, true)), 0), null)
        val selection = listOf(target("__key"), target("__version")) + mutation.assignments.map { SqlSelect(it.second, null) } + projection.inputs.map { target(it.column.name) }
        val query = query(selection, mutation.parameterCount).copy(from = writer.mutationSource(table, mutation), where = mutation.where,
            limit = SqlExpr.Literal((remainingMutations() + 1).toString(), LiteralKind.NUMBER, 0))
        val rows = writer.evaluate(query, parameters, SqlReadOptions(filterSoftDeleted = mutation.kind != SqlMutationKind.UNDELETE && mutation.kind != SqlMutationKind.HARD_DELETE))
        ensureCount(rows.size)
        if (mutation.joinedSource != null && rows.map { it[0] }.distinct().size != rows.size) {
            throw SqlException(SqlErrorCode.BINDING, "Joined mutation matches a target more than once")
        }
        for (row in rows) {
            budget.step()
            val key = Key<IsRootDataModel>((row[0] as SqlValue.Key).value.bytes)
            val version = (row[1] as SqlValue.UInt64).value
            val original = if (assignments.any { it.indices.size > 1 }) {
                budget.checkpoint(); budget.request()
                val fetched = sql.dataStore.execute(table.model.get(key, filterSoftDeleted = mutation.kind != SqlMutationKind.UNDELETE))
                budget.fetched(fetched.values.size)
                fetched.values.singleOrNull()?.values ?: throw SqlException(SqlErrorCode.CONSISTENCY, "Mutation target disappeared")
            } else null
            val pairs = writer.mutationPairs(table, assignments, row.subList(2, 2 + assignments.size), original)
            val member = if (mutation.kind == SqlMutationKind.DELETE || mutation.kind == SqlMutationKind.HARD_DELETE) table.model.delete(key, hardDelete = mutation.kind == SqlMutationKind.HARD_DELETE, lastVersion = version)
            else table.model.change(key.change(
                if (mutation.kind == SqlMutationKind.UNDELETE) ObjectSoftDeleteChange(false) else Change(*pairs.toTypedArray()),
                lastVersion = version,
            ))
            val input = projection.inputs.mapIndexed { column, definition ->
                val assignment = assignments.indexOfFirst { it.indices == definition.indices }
                if (mutation.kind == SqlMutationKind.UPDATE && assignment >= 0) definition.fromNative(writer.nativeValue(assignments[assignment], row[assignment + 2]))
                else row[2 + assignments.size + column]
            }
            add(index, member, returned(projection, parameters.take(projection.plan?.query?.parameterCount ?: parameters.size), projection.inputs.zip(input).toMap()))
        }
    }

    private suspend fun returned(projection: AtomicReturning, parameters: List<SqlValue>, row: InputRow): List<SqlValue> {
        val plan = projection.plan ?: return emptyList()
        val evaluator = SqlEvaluator(plan, parameters, budget)
        return plan.query.select.map { evaluator.eval(it.expression, row) }
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

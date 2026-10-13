package maryk.sql

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.shared.AtomicDataStore
import maryk.datastore.shared.ConditionalSnapshotVersionProvider
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.Transaction
import maryk.datastore.shared.TransactionException
import maryk.datastore.shared.TransactionResult
import maryk.datastore.shared.TransactionSavepoint
import maryk.datastore.shared.TransactionalDataStore
import maryk.datastore.shared.beginTransaction
import maryk.datastore.shared.captureSnapshotVersion
import maryk.datastore.shared.supportsSnapshotVersion
import maryk.datastore.shared.transaction
import maryk.sql.syntax.DEFAULT_MAX_SQL_TOKENS
import maryk.sql.syntax.SqlMutation
import maryk.sql.syntax.SqlLexer
import maryk.sql.syntax.SqlParseException
import maryk.sql.syntax.SqlTokenKind

internal class SqlTransactionStore(private val delegate: IsDataStore, private val transaction: Transaction) :
    IsDataStore by delegate, AtomicDataStore, TransactionalDataStore, ConditionalSnapshotVersionProvider {
    val readOnly get() = transaction.readOnly
    override val supportsSnapshotVersion get() = delegate.supportsSnapshotVersion
    override suspend fun captureSnapshotVersion() = delegate.captureSnapshotVersion()
    override val atomicCapabilities get() = requireNotNull(transactionCapabilities).storage
    override val supportsAtomicRequests = true
    override val transactionCapabilities get() = (delegate as TransactionalDataStore).transactionCapabilities
    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP =
        transaction.execute(request).response
    override suspend fun executeAtomic(request: AtomicRequest) = transaction.executeAtomic(request)
    override suspend fun executeTransaction(request: TransactionRequest) = transaction.executeBatch(request.requests)
    override suspend fun close() { transaction.rollback() }
}

/** Queries and RETURNING observe the staged view. Commit reports the durable versions. */
class SqlTransaction internal constructor(val transaction: Transaction, private val sql: MarykSql) {
    private val lock = Mutex()
    private suspend fun active() {
        try { transaction.ensureActive() }
        catch (error: TransactionException) { throw SqlException(SqlErrorCode.EXECUTION_STATE, error.message ?: "Transaction is unavailable", cause = error) }
    }
    fun prepareMutation(statement: String): PreparedSqlMutation = sql.prepareMutation(statement).inScope(lock, ::active)
    fun prepare(statement: String): PreparedSqlQuery = sql.prepare(statement).inScope(lock, ::active)
    suspend fun query(statement: String, parameters: List<SqlValue> = emptyList(), readOptions: SqlReadOptions = SqlReadOptions()): SqlResult =
        lock.withLock { active(); sql.query(statement, parameters, readOptions) }
    internal suspend fun prepare(statement: String, parameters: List<SqlValue>, parsed: SqlMutation? = null) =
        SqlAtomicWriter(sql).prepare(transaction.id, listOf(SqlWriteStatement(statement, parameters)), transactional = true, syntax = parsed?.let(::listOf))
    suspend fun execute(statement: String, parameters: List<SqlValue> = emptyList()): SqlAtomicWriteResult = lock.withLock { active(); prepare(statement, parameters).execute() }
    suspend fun savepoint(): TransactionSavepoint = lock.withLock { transaction.savepoint() }
    suspend fun rollbackTo(savepoint: TransactionSavepoint) = lock.withLock { transaction.rollbackTo(savepoint) }
    suspend fun release(savepoint: TransactionSavepoint) = lock.withLock { transaction.release(savepoint) }
    suspend fun commit(): TransactionResponse = lock.withLock { transaction.commit() }
    suspend fun rollback() = lock.withLock { transaction.rollback() }
}

suspend fun MarykSql.beginTransaction(readOnly: Boolean = false): SqlTransaction {
    val tx = dataStore.beginTransaction(readOnly = readOnly)
    return SqlTransaction(tx, MarykSql.create(SqlTransactionStore(dataStore, tx), catalog, options))
}

suspend fun <T> MarykSql.transaction(readOnly: Boolean = false, block: suspend (SqlTransaction) -> T): TransactionResult<T> = dataStore.transaction(readOnly) { tx ->
    block(SqlTransaction(tx, MarykSql.create(SqlTransactionStore(dataStore, tx), catalog, options)))
}

sealed interface SqlSessionResult {
    data class Query(val result: SqlResult) : SqlSessionResult
    data class Mutation(val result: SqlAtomicWriteResult) : SqlSessionResult
    data class Control(val command: String, val commit: TransactionResponse? = null) : SqlSessionResult
}

/** One sequential connection-style scope. Closing rolls back a transaction that has not submitted commit. */
class SqlSession internal constructor(private val sql: MarykSql) {
    private val lock = Mutex()
    private var current: SqlTransaction? = null
    private val savepoints = linkedMapOf<String, TransactionSavepoint>()
    val activeTransaction: SqlTransaction? get() = current

    suspend fun execute(statement: String, parameters: List<SqlValue> = emptyList(), readOptions: SqlReadOptions = SqlReadOptions()): SqlSessionResult = lock.withLock {
        executeLocked(statement, parameters, readOptions)
    }

    private suspend fun executeLocked(statement: String, parameters: List<SqlValue>, readOptions: SqlReadOptions): SqlSessionResult {
        val tokens = try { SqlLexer(statement, DEFAULT_MAX_SQL_TOKENS).tokenize().dropLast(1).toMutableList() }
        catch (error: SqlParseException) { throw error.toSqlException() }
        if (tokens.lastOrNull()?.text == ";") tokens.removeAt(tokens.lastIndex)
        val first = tokens.firstOrNull() ?: throw SqlException(SqlErrorCode.SYNTAX, "Empty SQL statement")
        fun syntax(): Nothing = throw SqlException(SqlErrorCode.SYNTAX, "Invalid transaction command", first.position)
        fun tx() = current ?: throw SqlException(SqlErrorCode.EXECUTION_STATE, "No transaction is active")
        fun name(index: Int): String {
            val token = tokens.getOrNull(index) ?: syntax()
            if (token.kind != SqlTokenKind.IDENTIFIER && token.kind != SqlTokenKind.QUOTED_IDENTIFIER) syntax()
            return if (token.kind == SqlTokenKind.QUOTED_IDENTIFIER) token.text else asciiLower(token.text)
        }
        val control = listOf("BEGIN", "START", "COMMIT", "ROLLBACK", "SAVEPOINT", "RELEASE").any(first::isKeyword)
        if (control && parameters.isNotEmpty()) throw SqlException(SqlErrorCode.PARAMETER, "Transaction commands do not accept parameters")
        when {
            first.isKeyword("BEGIN") || first.isKeyword("START") -> {
                var offset = 1
                if (tokens.getOrNull(offset)?.isKeyword("TRANSACTION") == true) offset++
                else if (first.isKeyword("START")) syntax()
                var readOnly = false
                if (tokens.getOrNull(offset)?.isKeyword("READ") == true) {
                    val mode = tokens.getOrNull(offset + 1) ?: syntax()
                    if (!mode.isKeyword("ONLY") && !mode.isKeyword("WRITE")) syntax()
                    readOnly = mode.isKeyword("ONLY")
                    offset += 2
                }
                if (offset != tokens.size) syntax()
                if (current != null) throw SqlException(SqlErrorCode.EXECUTION_STATE, "A transaction is already active")
                current = sql.beginTransaction(readOnly)
                return SqlSessionResult.Control("BEGIN")
            }
            first.isKeyword("COMMIT") -> {
                if (tokens.size != 1 && !(tokens.size == 2 && tokens[1].isKeyword("WORK"))) syntax()
                val result = tx().commit()
                if (result.status != TransactionStatus.OutcomeUnknown) { current = null; savepoints.clear() }
                return SqlSessionResult.Control("COMMIT", result)
            }
            first.isKeyword("ROLLBACK") -> {
                if (tokens.size == 1 || tokens.size == 2 && tokens[1].isKeyword("WORK")) {
                    tx().rollback(); current = null; savepoints.clear()
                    return SqlSessionResult.Control("ROLLBACK")
                }
                if (!tokens.getOrNull(1).let { it?.isKeyword("TO") == true }) syntax()
                val index = if (tokens.getOrNull(2)?.isKeyword("SAVEPOINT") == true) 3 else 2
                if (tokens.size != index + 1) syntax()
                val selected = name(index)
                tx().rollbackTo(savepoints[selected] ?: throw SqlException(SqlErrorCode.EXECUTION_STATE, "Unknown savepoint '$selected'"))
                val after = savepoints.keys.dropWhile { it != selected }.drop(1)
                after.forEach(savepoints::remove)
                return SqlSessionResult.Control("ROLLBACK TO")
            }
            first.isKeyword("SAVEPOINT") -> {
                if (tokens.size != 2) syntax()
                val selected = name(1)
                if (selected in savepoints) throw SqlException(SqlErrorCode.EXECUTION_STATE, "Savepoint '$selected' already exists")
                savepoints[selected] = tx().savepoint()
                return SqlSessionResult.Control("SAVEPOINT")
            }
            first.isKeyword("RELEASE") -> {
                val index = if (tokens.getOrNull(1)?.isKeyword("SAVEPOINT") == true) 2 else 1
                if (tokens.size != index + 1) syntax()
                val selected = name(index)
                tx().release(savepoints[selected] ?: throw SqlException(SqlErrorCode.EXECUTION_STATE, "Unknown savepoint '$selected'"))
                savepoints.keys.dropWhile { it != selected }.toList().forEach(savepoints::remove)
                return SqlSessionResult.Control("RELEASE")
            }
        }
        if (listOf("INSERT", "UPDATE", "DELETE", "UNDELETE").any(first::isKeyword)) {
            val active = current
            return SqlSessionResult.Mutation(if (active != null) active.execute(statement, parameters) else sql.execute(statement, parameters))
        }
        return SqlSessionResult.Query(current?.query(statement, parameters, readOptions) ?: sql.query(statement, parameters, readOptions))
    }

    suspend fun resolveCommit(): TransactionResponse = lock.withLock {
        val tx = current ?: throw SqlException(SqlErrorCode.EXECUTION_STATE, "No transaction is active")
        val result = tx.transaction.resolveCommit()
        if (result.status != TransactionStatus.OutcomeUnknown) { current = null; savepoints.clear() }
        result
    }

    suspend fun close() = lock.withLock {
        current?.rollback()
        current = null
        savepoints.clear()
    }
}

fun MarykSql.session(): SqlSession = SqlSession(this)


/** Classification uses the same lexer as execution, including leading comments. */
enum class SqlStatementKind { QUERY, MUTATION, CONTROL }

fun sqlStatementKind(statement: String): SqlStatementKind {
    val first = try { SqlLexer(statement, DEFAULT_MAX_SQL_TOKENS).tokenize().first() }
    catch (error: SqlParseException) { throw error.toSqlException() }
    return when {
        listOf("BEGIN", "START", "COMMIT", "ROLLBACK", "SAVEPOINT", "RELEASE").any(first::isKeyword) -> SqlStatementKind.CONTROL
        listOf("INSERT", "UPDATE", "DELETE", "UNDELETE").any(first::isKeyword) -> SqlStatementKind.MUTATION
        else -> SqlStatementKind.QUERY
    }
}

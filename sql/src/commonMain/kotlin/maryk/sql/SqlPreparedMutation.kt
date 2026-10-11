package maryk.sql

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maryk.sql.syntax.LiteralKind
import maryk.sql.syntax.SqlExpr
import maryk.sql.syntax.SqlJoin
import maryk.sql.syntax.SqlJoinType
import maryk.sql.syntax.SqlMutation
import maryk.sql.syntax.SqlMutationKind
import maryk.sql.syntax.SqlName
import maryk.sql.syntax.SqlParseException
import maryk.sql.syntax.SqlParser
import maryk.sql.syntax.SqlQuery
import maryk.sql.syntax.SqlSelect
import maryk.sql.syntax.SqlTableSource

/** Binding reads no records. Each execution starts a new autocommit or joins its owning scope. */
class PreparedSqlMutation internal constructor(private val sql: MarykSql, private val statement: String, private val syntax: SqlMutation, private val binding: PreparedSqlQuery, private val lock: Mutex? = null, private val validateScope: (suspend () -> Unit)? = null) {
    val parameters get() = binding.parameters
    internal fun inScope(lock: Mutex, validate: suspend () -> Unit) = PreparedSqlMutation(sql, statement, syntax, binding, lock, validate)
    suspend fun execute(parameters: List<SqlValue> = emptyList()): SqlAtomicWriteResult {
        val bound = binding.bindParameters(parameters)
        return if (lock == null) sql.executePrepared(statement, bound, syntax) else lock.withLock { validateScope?.invoke(); sql.executePrepared(statement, bound, syntax) }
    }
}

fun MarykSql.prepareMutation(statement: String): PreparedSqlMutation {
    val mutation = try { SqlParser(statement).parseMutation() } catch (error: SqlParseException) { throw error.toSqlException() }
    val writer = SqlWriter(this)
    writer.validateExpressions(mutation)
    val table = writer.table(mutation.table)
    val columns = writer.columns(table, mutation.columns)
    writer.columns(table, mutation.assignments.map { it.first })
    mutation.conflict?.let { writer.conflictColumns(table, it); writer.columns(table, it.assignments.map { pair -> pair.first }) }
    val source = writer.mutationSource(table, mutation)
    fun query(select: List<SqlSelect>, from: SqlTableSource? = null, where: SqlExpr? = null) = SqlQuery(select, from, where, emptyList(), null, emptyList(), null, null, false, mutation.parameterCount)
    val state = BindingState()
    var last: BoundQuery? = null
    fun bind(query: SqlQuery): BoundQuery = SqlBinder(catalog, options, state).bind(query).also { last = it }
    val assignments = writer.columns(table, mutation.assignments.map { it.first })
    val contexts = mutation.rows.flatMap { row -> columns.zip(row) } + assignments.zip(mutation.assignments.map { it.second })
    for ((column, expression) in contexts) if (expression is SqlExpr.Parameter) {
        val previous = state.parameterTypes.put(expression.index, column.column.type)
        if (previous != null && previous != column.column.type) throw SqlException(SqlErrorCode.PARAMETER, "Parameter has conflicting write types", expression.position)
        if (column.domain != null) state.parameterDomains[expression.index] = column
    }
    if (mutation.kind == SqlMutationKind.INSERT) {
        if (mutation.rows.any { it.size != mutation.columns.size }) throw SqlException(SqlErrorCode.SYNTAX, "INSERT width does not match columns")
        if (mutation.rows.isNotEmpty()) bind(query(mutation.rows.flatten().map { SqlSelect(it, null) }))
        mutation.sourceQuery?.let { if (bind(it).columns.size != mutation.columns.size) throw SqlException(SqlErrorCode.SYNTAX, "INSERT width does not match columns") }
    } else bind(query(mutation.assignments.map { SqlSelect(it.second, null) }.ifEmpty { listOf(SqlSelect(SqlExpr.Literal("1", LiteralKind.NUMBER, 0), null)) }, source, mutation.where))
    if (mutation.returning.isNotEmpty()) { SqlAtomicWriter(this).bindReturning(table, mutation); bind(query(mutation.returning, writer.mutationSource(table, mutation, joined = false))) }
    mutation.conflict?.takeIf { it.assignments.isNotEmpty() }?.let { conflict ->
        val excluded = source.copy(alias = SqlName("excluded"))
        bind(query(conflict.assignments.map { SqlSelect(it.second, null) }, source.copy(joins = listOf(SqlJoin(excluded, SqlJoinType.CROSS, null)))))
    }
    return PreparedSqlMutation(this, statement, mutation, PreparedSqlQuery(this, requireNotNull(last)))
}

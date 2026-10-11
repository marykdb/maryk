package maryk.datastore.shared

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.query.changes.Check
import maryk.core.query.changes.change
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.AtomicRequestLimitException
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.TransactionCapabilities
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.responses.AtomicFailure
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.ValuesResponse

internal class TransactionScopeContext(val owner: IsDataStore, val transaction: Transaction) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionScopeContext>
}

suspend fun currentTransaction(store: IsDataStore): Transaction? =
    currentCoroutineContext()[TransactionScopeContext]?.takeIf { it.owner === store }?.transaction

interface TransactionalDataStore {
    val transactionCapabilities: TransactionCapabilities?
    suspend fun executeTransaction(request: TransactionRequest): TransactionResponse
}

val IsDataStore.transactionCapabilities: TransactionCapabilities?
    get() = (this as? TransactionalDataStore)?.transactionCapabilities

suspend fun IsDataStore.executeTransaction(request: TransactionRequest): TransactionResponse =
    (this as? TransactionalDataStore)?.takeIf { it.transactionCapabilities != null }?.executeTransaction(request)
        ?: TransactionResponse(TransactionStatus.Unsupported, message = "This datastore does not support transactions")

class TransactionCancellationException(val transaction: Transaction, override val cause: Throwable) :
    CancellationException("Transaction commit outcome is unknown; resolve the retained transaction")

class TransactionException(val response: TransactionResponse) : RequestException(response.message ?: "Transaction ${response.status}: ${response.failure}; ${response.responses}")

enum class TransactionState { Active, Committed, RolledBack, Aborted, OutcomeUnknown, Expired }

/** The response describes the private staged view, including transaction-local versions. */
data class TransactionStep<RP : IsResponse>(val response: RP, val transactionId: String, val provisional: Boolean = true)
data class TransactionResult<T>(val value: T?, val commit: TransactionResponse, val transaction: Transaction)
class TransactionSavepoint internal constructor(internal val owner: Any, internal val ordinal: Long, internal val position: Int)

/** An owned, sequential transaction. No backend resources are retained while user code suspends. */
class Transaction internal constructor(
    private val store: IsDataStore,
    val id: String,
    private val opened: TransactionResponse,
    private val capabilities: TransactionCapabilities,
    val readOnly: Boolean,
) {
    private val lock = Mutex()
    private val started = TimeSource.Monotonic.markNow()
    private val journal = mutableListOf<IsStoreRequest<*, *>>()
    private val savepoints = mutableListOf<TransactionSavepoint>()
    private var savepointOrdinal = 0L
    private var committedRequest: TransactionRequest? = null
    var state: TransactionState = TransactionState.Active
        private set
    val stagingVersion: ULong get() = requireNotNull(opened.version)
    val operationCount: Int get() = journal.size
    private var lastCommit: TransactionResponse? = null
    private var lastAbort: TransactionResponse? = null

    private fun normalize(requests: List<IsStoreRequest<*, *>>): List<IsStoreRequest<*, *>> = try {
        AtomicReceiptCodec(store.dataModelsById).snapshotTransactionRequest(
            TransactionRequest(id, requests, readOnly = readOnly),
        ).requests
    } catch (limit: AtomicRequestLimitException) {
        throw TransactionException(TransactionResponse(TransactionStatus.Aborted,
            failure = AtomicFailure(AtomicFailureCode.LimitExceeded, limit.limit, limit.maximum.toULong(), limit.actual.toULong())))
    } catch (abort: AtomicAbort) {
        throw TransactionException(abort.response.asTransactionResponse())
    }

    /** Check scope state and lifetime without reading or changing its journal. */
    suspend fun ensureActive() = lock.withLock { active() }

    private fun active() {
        if (state != TransactionState.Active) throw TransactionException(TransactionResponse(TransactionStatus.Aborted,
            message = "Transaction is $state", failure = AtomicFailure(AtomicFailureCode.InvalidState)))
        if (started.elapsedNow().inWholeMilliseconds >= capabilities.maxLifetimeMillis.toLong()) {
            state = TransactionState.Expired
            throw TransactionException(TransactionResponse(TransactionStatus.Aborted,
                message = "Transaction expired", failure = AtomicFailure(AtomicFailureCode.Expired)))
        }
    }

    private fun request(commit: Boolean): TransactionRequest = try {
        TransactionRequest(id, journal.toList(), opened.revision, stagingVersion, commit, readOnly = readOnly).also { request ->
            if (request.mutationCount > capabilities.storage.maxMutations) throw AtomicRequestLimitException("mutations", capabilities.storage.maxMutations, request.mutationCount)
            if (request.readKeyCount > capabilities.maxReadKeys) throw AtomicRequestLimitException("readKeys", capabilities.maxReadKeys, request.readKeyCount)
        }.let { AtomicReceiptCodec(store.dataModelsById).snapshotTransactionRequest(it) }
    } catch (limit: AtomicRequestLimitException) {
        throw TransactionException(TransactionResponse(TransactionStatus.Aborted,
            failure = AtomicFailure(AtomicFailureCode.LimitExceeded, limit.limit, limit.maximum.toULong(), limit.actual.toULong())))
    } catch (abort: AtomicAbort) {
        throw TransactionException(abort.response.asTransactionResponse())
    }

    private fun validatePreview(response: TransactionResponse) {
        if (response.status != TransactionStatus.Staged) throw TransactionException(response)
        val rows = response.responses.filterIsInstance<ValuesResponse<*>>().sumOf { it.values.size.toLong() }
        if (rows > capabilities.maxReadRows.toLong()) throw TransactionException(TransactionResponse(TransactionStatus.Aborted,
            failure = AtomicFailure(AtomicFailureCode.LimitExceeded, "readRows", capabilities.maxReadRows.toULong(), rows.toULong())))
    }

    suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): TransactionStep<RP> = lock.withLock {
        active()
        val normalized = normalize(listOf(request)).single()
        if (journal.size.toUInt() >= capabilities.maxOperations) throw TransactionException(TransactionResponse(TransactionStatus.Aborted,
            message = "Transaction operation limit exceeded", failure = AtomicFailure(AtomicFailureCode.LimitExceeded,
                "operations", capabilities.maxOperations.toULong(), journal.size.toULong() + 1u)))
        journal += normalized
        try {
            val response = store.executeTransaction(request(false))
            validatePreview(response)
            @Suppress("UNCHECKED_CAST")
            TransactionStep(response.responses.last() as RP, id)
        } catch (error: Throwable) {
            state = TransactionState.Aborted
            if (error is TransactionException) lastAbort = error.response
            throw error
        }
    }

    /** Stage a bounded group without replaying its intermediate prefixes. */
    suspend fun executeBatch(requests: List<IsStoreRequest<*, *>>): TransactionResponse = lock.withLock {
        active()
        val count = journal.size + requests.size
        if (count.toUInt() > capabilities.maxOperations) throw TransactionException(TransactionResponse(TransactionStatus.Aborted,
            failure = AtomicFailure(AtomicFailureCode.LimitExceeded, "operations", capabilities.maxOperations.toULong(), count.toULong())))
        val normalized = normalize(requests)
        journal.addAll(normalized)
        try {
            val response = store.executeTransaction(request(false))
            validatePreview(response)
            response.copy(responses = response.responses.takeLast(normalized.size))
        } catch (error: Throwable) {
            state = TransactionState.Aborted
            if (error is TransactionException) lastAbort = error.response
            throw error
        }
    }

    suspend fun executeAtomic(request: AtomicRequest): AtomicResponse {
        val conditions = request.requests.mapNotNull { member ->
            when (member) {
                is ChangeRequest<*> -> member.objects.single().let { obj ->
                    if (obj.lastVersion != null || obj.changes.any { it is Check }) member.dataModel.change(
                        obj.key.change(*obj.changes.filterIsInstance<Check>().toTypedArray(), lastVersion = obj.lastVersion),
                    ) else null
                }
                is DeleteRequest<*> -> member.lastVersion?.let {
                    member.dataModel.change(member.keys.single().change(lastVersion = it))
                }
                else -> null
            }
        }
        val mutations = request.requests.map { member ->
            when (member) {
                is ChangeRequest<*> -> member.objects.single().let { obj ->
                    member.dataModel.change(obj.key.change(*obj.changes.filterNot { it is Check }.toTypedArray()))
                }
                is DeleteRequest<*> -> member.dataModel.delete(member.keys.single())
                else -> member
            }
        }
        val response = executeBatch(request.guards + conditions + mutations)
        return AtomicResponse(AtomicStatus.Staged, response.responses.takeLast(request.requests.size))
    }

    suspend fun savepoint(): TransactionSavepoint = lock.withLock {
        active()
        if (savepoints.size.toUInt() >= capabilities.maxSavepoints) throw TransactionException(TransactionResponse(TransactionStatus.Aborted,
            failure = AtomicFailure(AtomicFailureCode.LimitExceeded, "savepoints", capabilities.maxSavepoints.toULong(), savepoints.size.toULong() + 1u)))
        TransactionSavepoint(this, ++savepointOrdinal, journal.size).also { savepoints += it }
    }

    suspend fun rollbackTo(savepoint: TransactionSavepoint) = lock.withLock {
        require(savepoint.owner === this && savepoint in savepoints) { "Savepoint is not active in this transaction" }
        if (state == TransactionState.Aborted && lastAbort?.failure?.code in setOf(AtomicFailureCode.ValidationFailed, AtomicFailureCode.LimitExceeded)) {
            state = TransactionState.Active
            lastAbort = null
        }
        active()
        while (journal.size > savepoint.position) journal.removeAt(journal.lastIndex)
        savepoints.removeAll { it.ordinal > savepoint.ordinal }
    }

    suspend fun release(savepoint: TransactionSavepoint) = lock.withLock {
        active()
        require(savepoint.owner === this && savepoint in savepoints) { "Savepoint is not active in this transaction" }
        savepoints.removeAll { it.ordinal >= savepoint.ordinal }
    }

    suspend fun <T> savepoint(block: suspend (Transaction) -> T): T {
        val point = savepoint()
        return try { block(this).also { release(point) } }
        catch (error: Throwable) {
            if (state == TransactionState.Active || state == TransactionState.Aborted &&
                lastAbort?.failure?.code in setOf(AtomicFailureCode.ValidationFailed, AtomicFailureCode.LimitExceeded)) rollbackTo(point)
            throw error
        }
    }

    suspend fun commit(): TransactionResponse = lock.withLock {
        if (state == TransactionState.Committed) return@withLock requireNotNull(lastCommit)
        if (state == TransactionState.OutcomeUnknown) return@withLock resolveLocked()
        active()
        committedRequest = request(true)
        state = TransactionState.OutcomeUnknown
        resolveLocked()
    }

    private suspend fun resolveLocked(): TransactionResponse {
        val response = try { store.executeTransaction(requireNotNull(committedRequest)) }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            error.rethrowIfFatal()
            TransactionResponse(TransactionStatus.OutcomeUnknown, message = error.message)
        }
        lastCommit = response
        state = when (response.status) {
            TransactionStatus.Committed -> TransactionState.Committed
            TransactionStatus.OutcomeUnknown -> TransactionState.OutcomeUnknown
            else -> TransactionState.Aborted
        }
        return response
    }

    suspend fun resolveCommit(): TransactionResponse = lock.withLock {
        if (state == TransactionState.Committed) requireNotNull(lastCommit)
        else { check(state == TransactionState.OutcomeUnknown) { "Commit is not uncertain" }; resolveLocked() }
    }

    suspend fun rollback() = lock.withLock {
        check(state != TransactionState.Committed && state != TransactionState.OutcomeUnknown) { "A submitted commit cannot be rolled back" }
        journal.clear()
        savepoints.clear()
        state = TransactionState.RolledBack
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun IsDataStore.beginTransaction(id: String = Uuid.random().toString(), readOnly: Boolean = false): Transaction {
    check(currentTransaction(this) == null) { "Use a savepoint for nested transaction scopes" }
    val capabilities = transactionCapabilities ?: throw TransactionException(TransactionResponse(TransactionStatus.Unsupported))
    val response = executeTransaction(TransactionRequest(id, readOnly = readOnly))
    if (response.status != TransactionStatus.Staged) throw TransactionException(response)
    return Transaction(this, id, response, capabilities, readOnly)
}

suspend fun <T> IsDataStore.transaction(readOnly: Boolean = false, block: suspend (Transaction) -> T): TransactionResult<T> {
    val tx = beginTransaction(readOnly = readOnly)
    return try {
        val completed = withContext(DISPATCHER + TransactionScopeContext(this, tx)) { withTimeoutOrNull(transactionCapabilities!!.maxLifetimeMillis.toLong()) {
            val value = block(tx)
            val commit = tx.commit()
            TransactionResult(if (commit.status == TransactionStatus.Committed) value else null, commit, tx)
        } }
        if (completed != null) completed else {
            val uncertain = tx.state == TransactionState.OutcomeUnknown
            if (!uncertain) tx.rollback()
            TransactionResult(null, TransactionResponse(
            if (uncertain) TransactionStatus.OutcomeUnknown else TransactionStatus.Aborted,
            message = "Transaction deadline exceeded", failure = AtomicFailure(AtomicFailureCode.Expired)), tx)
        }
    } catch (error: Throwable) {
        if (tx.state == TransactionState.Active || tx.state == TransactionState.Aborted || tx.state == TransactionState.Expired) tx.rollback()
        if (error is CancellationException && tx.state == TransactionState.OutcomeUnknown) throw TransactionCancellationException(tx, error)
        throw error
    }
}

/** Explicitly reruns the whole callback on a conflict, at most three attempts. Keep external effects outside this callback. */
suspend fun <T> IsDataStore.transactionWithRetry(readOnly: Boolean = false, block: suspend (Transaction) -> T): TransactionResult<T> {
    repeat(3) { attempt ->
        try {
            val result = transaction(readOnly, block)
            if (result.commit.failure?.code != AtomicFailureCode.Conflict || attempt == 2) return result
        } catch (error: TransactionException) {
            if (error.response.failure?.code != AtomicFailureCode.Conflict || attempt == 2) throw error
        }
        delay(25L shl attempt)
    }
    error("Retry loop exhausted")
}

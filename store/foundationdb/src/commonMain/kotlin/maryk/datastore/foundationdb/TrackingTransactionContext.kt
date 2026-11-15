package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import maryk.foundationdb.Database
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.datastore.shared.rethrowIfFatal

/** Tracks owned transactions and futures so datastore close can cancel outstanding work. */
internal class TrackingTransactionContext(
    private val delegate: TransactionContext,
    private val configure: (Transaction, FoundationDBTransactionContext) -> Unit = { _, _ -> },
) : TransactionContext {
    private val closing = atomic(false)
    private class TransactionState { val mutations = atomic(0) }
    private val activeTransactions = atomic<Map<Transaction, TransactionState>>(emptyMap())
    private val activeFutures = atomic<Set<FdbFuture<*>>>(emptySet())
    internal val peakActiveTransactions = atomic(0)

    fun markMutation(transaction: Transaction) {
        val state = activeTransactions.value[transaction]
            ?: throw CancellationException("Transaction is no longer active")
        state.mutations.incrementAndGet()
    }

    fun mutationCount(transaction: Transaction): Int = activeTransactions.value[transaction]?.mutations?.value ?: 0

    override fun <T> run(block: (Transaction) -> T): T =
        runConfigured(FoundationDBTransactionContext(FoundationDBTransactionKind.Maintenance), block)

    fun <T> runConfigured(context: FoundationDBTransactionContext, block: (Transaction) -> T): T {
        var firstTransaction: Transaction? = null
        var ownedTransactions: MutableSet<Transaction>? = null
        try {
            return delegate.run { transaction ->
                register(transaction)
                val first = firstTransaction
                if (first == null) firstTransaction = transaction
                else if (transaction != first) {
                    (ownedTransactions ?: mutableSetOf(first).also { ownedTransactions = it }).add(transaction)
                }
                configure(transaction, context)
                block(transaction)
            }
        } finally {
            val owned = ownedTransactions
            if (owned != null) removeTransactions(owned)
            else firstTransaction?.let { transaction ->
                activeTransactions.update { transactions ->
                    if (transactions.size == 1 && transactions.containsKey(transaction)) emptyMap()
                    else transactions - transaction
                }
            }
        }
    }

    /** Own one transaction across native onError retries without future/coroutine adapters. */
    internal suspend fun <T> runSuspendingConfigured(
        context: FoundationDBTransactionContext,
        block: suspend (Transaction) -> T,
    ): T {
        val database = delegate as? Database ?: return runSuspendingThroughAsync(context, block)
        currentCoroutineContext().ensureActive()
        if (closing.value) throw CancellationException("Datastore closing")
        val transaction = database.createTransaction()
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                register(transaction)
                try {
                    configure(transaction, context)
                    val value = coroutineScope { block(transaction) }
                    currentCoroutineContext().ensureActive()
                    if (closing.value) throw CancellationException("Datastore closing")
                    transaction.commit().await()
                    return value
                } catch (error: Throwable) {
                    error.rethrowIfFatal()
                    currentCoroutineContext().ensureActive()
                    if (closing.value) throw CancellationException("Datastore closing")
                    // Reset the same portable owner: native retry counts, timeout and
                    // backoff survive, and the SDK owns any replacement handle.
                    transaction.onError(error).await()
                }
            }
        } finally {
            try {
                activeTransactions.update { transactions ->
                    when {
                        transactions.isEmpty() -> transactions
                        transactions.size == 1 && transactions.containsKey(transaction) -> emptyMap()
                        else -> transactions - transaction
                    }
                }
            } finally {
                transaction.close()
            }
        }
    }

    override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> =
        runAsyncConfigured(FoundationDBTransactionContext(FoundationDBTransactionKind.Maintenance), block)

    fun <T> runAsyncConfigured(context: FoundationDBTransactionContext, block: (Transaction) -> FdbFuture<T>): FdbFuture<T> {
        if (closing.value) throw CancellationException("Datastore closing")
        val ownedTransactions = atomic<Set<Transaction>>(emptySet())
        val delegateFuture = try {
            delegate.runAsync { transaction ->
                register(transaction)
                ownedTransactions.update { it.withTracked(transaction) }
                configure(transaction, context)
                block(transaction)
            }
        } catch (error: Throwable) {
            val owned = ownedTransactions.getAndSet(emptySet())
            removeTransactions(owned)
            throw error
        }
        lateinit var trackedFuture: TrackedFdbFuture<T>
        trackedFuture = TrackedFdbFuture(delegateFuture, cancelNative = {
            ownedTransactions.value.forEach { transaction ->
                try { transaction.cancel() } catch (error: Throwable) { error.rethrowIfFatal() }
            }
        }) {
            val owned = ownedTransactions.getAndSet(emptySet())
            removeTransactions(owned)
            activeFutures.update { it.withoutTracked(trackedFuture) }
        }
        activeFutures.update { it.withTracked(trackedFuture) }
        if (closing.value) {
            activeFutures.update { it.withoutTracked(trackedFuture) }
            trackedFuture.cancel()
        }
        return trackedFuture
    }

    override fun <T> read(block: (ReadTransaction) -> T): T = run(block)

    override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> = runAsync(block)

    fun cancelActive() {
        closing.value = true
        val futures = activeFutures.getAndSet(emptySet())
        val transactions = activeTransactions.getAndSet(emptyMap()).keys
        transactions.forEach { transaction ->
            try { transaction.cancel() } catch (error: Throwable) { error.rethrowIfFatal() }
        }
        futures.forEach(FdbFuture<*>::cancel)
        transactions.forEach(Transaction::close)
    }

    private fun register(transaction: Transaction) {
        if (closing.value) {
            transaction.close()
            throw CancellationException("Datastore closing")
        }
        activeTransactions.update { it + (transaction to TransactionState()) }
        peakActiveTransactions.update { maxOf(it, activeTransactions.value.size) }
        if (closing.value) {
            activeTransactions.update { it - transaction }
            transaction.close()
            throw CancellationException("Datastore closing")
        }
    }

    private fun removeTransactions(owned: Set<Transaction>) {
        if (owned.isEmpty()) return
        activeTransactions.update { transactions ->
            when {
                transactions.isEmpty() -> transactions
                transactions.size == 1 && owned.any { transactions.containsKey(it) } -> emptyMap()
                else -> transactions - owned
            }
        }
    }
}

private fun <T> Set<T>.withTracked(value: T): Set<T> = when {
    contains(value) -> this
    isEmpty() -> setOf(value)
    else -> this + value
}

private fun <T> Set<T>.withoutTracked(value: T): Set<T> = when {
    !contains(value) -> this
    size == 1 -> emptySet()
    else -> this - value
}

private class TrackedFdbFuture<T>(
    private val delegate: FdbFuture<T>,
    private val cancelNative: () -> Unit,
    private val cleanup: () -> Unit,
) : FdbFuture<T> {
    private val cleaned = atomic(false)

    override suspend fun awaitObserved(): T = try { delegate.awaitObserved() } finally { if (delegate.isDone) cleanOnce() }

    override suspend fun await(): T = try {
        delegate.await()
    } catch (error: CancellationException) {
        cancel()
        throw error
    } finally {
        cleanOnce()
    }

    override fun cancel() {
        try {
            cancelNative()
            delegate.cancel()
        } finally {
            cleanOnce()
        }
    }

    override val isDone: Boolean
        get() = delegate.isDone.also { if (it) cleanOnce() }

    override val isCancelled: Boolean
        get() = delegate.isCancelled.also { if (it) cleanOnce() }

    private fun cleanOnce() {
        if (cleaned.compareAndSet(expect = false, update = true)) cleanup()
    }
}

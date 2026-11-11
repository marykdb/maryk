package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CancellationException
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.datastore.shared.rethrowIfFatal

/** Tracks blocking transactions so datastore close can cancel their outstanding FDB futures. */
internal class TrackingTransactionContext(
    private val delegate: TransactionContext,
    private val configure: (Transaction, FoundationDBTransactionContext) -> Unit = { _, _ -> },
) : TransactionContext {
    private val closing = atomic(false)
    private val activeTransactions = atomic<Set<Transaction>>(emptySet())
    private val activeFutures = atomic<Set<FdbFuture<*>>>(emptySet())
    private val logicalMutations = atomic<Map<Transaction, Int>>(emptyMap())
    internal val peakActiveTransactions = atomic(0)

    fun markMutation(transaction: Transaction) {
        logicalMutations.update { it + (transaction to ((it[transaction] ?: 0) + 1)) }
    }

    fun mutationCount(transaction: Transaction): Int = logicalMutations.value[transaction] ?: 0

    override fun <T> run(block: (Transaction) -> T): T =
        runConfigured(FoundationDBTransactionContext(FoundationDBTransactionKind.Maintenance), block)

    fun <T> runConfigured(context: FoundationDBTransactionContext, block: (Transaction) -> T): T {
        val ownedTransactions = mutableSetOf<Transaction>()
        try {
            return delegate.run { transaction ->
                register(transaction)
                ownedTransactions += transaction
                configure(transaction, context)
                block(transaction)
            }
        } finally {
            activeTransactions.update { it - ownedTransactions }
            logicalMutations.update { it - ownedTransactions }
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
                ownedTransactions.update { it + transaction }
                configure(transaction, context)
                block(transaction)
            }
        } catch (error: Throwable) {
            val owned = ownedTransactions.getAndSet(emptySet())
            activeTransactions.update { it - owned }
            logicalMutations.update { it - owned }
            throw error
        }
        lateinit var trackedFuture: TrackedFdbFuture<T>
        trackedFuture = TrackedFdbFuture(delegateFuture, cancelNative = {
            ownedTransactions.value.forEach { transaction ->
                try { transaction.cancel() } catch (error: Throwable) { error.rethrowIfFatal() }
            }
        }) {
            val owned = ownedTransactions.getAndSet(emptySet())
            activeTransactions.update { it - owned }
            logicalMutations.update { it - owned }
            activeFutures.update { it - trackedFuture }
        }
        activeFutures.update { it + trackedFuture }
        if (closing.value) {
            activeFutures.update { it - trackedFuture }
            trackedFuture.cancel()
        }
        return trackedFuture
    }

    override fun <T> read(block: (ReadTransaction) -> T): T = run(block)

    override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> = runAsync(block)

    fun cancelActive() {
        closing.value = true
        val futures = activeFutures.getAndSet(emptySet())
        val transactions = activeTransactions.getAndSet(emptySet())
        logicalMutations.value = emptyMap()
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
        activeTransactions.update { it + transaction }
        peakActiveTransactions.update { maxOf(it, activeTransactions.value.size) }
        logicalMutations.update { it + (transaction to 0) }
        if (closing.value) {
            activeTransactions.update { it - transaction }
            logicalMutations.update { it - transaction }
            transaction.close()
            throw CancellationException("Datastore closing")
        }
    }
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

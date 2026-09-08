package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CancellationException
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext

/** Tracks blocking transactions so datastore close can cancel their outstanding FDB futures. */
internal class TrackingTransactionContext(
    private val delegate: TransactionContext,
) : TransactionContext {
    private val closing = atomic(false)
    private val activeTransactions = atomic<Set<Transaction>>(emptySet())
    private val activeFutures = atomic<Set<FdbFuture<*>>>(emptySet())

    override fun <T> run(block: (Transaction) -> T): T {
        val ownedTransactions = mutableSetOf<Transaction>()
        try {
            return delegate.run { transaction ->
                register(transaction)
                ownedTransactions += transaction
                block(transaction)
            }
        } finally {
            activeTransactions.update { it - ownedTransactions }
        }
    }

    override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> {
        if (closing.value) throw CancellationException("Datastore closing")
        val ownedTransactions = atomic<Set<Transaction>>(emptySet())
        val delegateFuture = try {
            delegate.runAsync { transaction ->
                register(transaction)
                ownedTransactions.update { it + transaction }
                block(transaction)
            }
        } catch (error: Throwable) {
            activeTransactions.update { it - ownedTransactions.getAndSet(emptySet()) }
            throw error
        }
        lateinit var trackedFuture: TrackedFdbFuture<T>
        trackedFuture = TrackedFdbFuture(delegateFuture) {
            activeTransactions.update { it - ownedTransactions.getAndSet(emptySet()) }
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
        futures.forEach(FdbFuture<*>::cancel)
        transactions.forEach(Transaction::close)
    }

    private fun register(transaction: Transaction) {
        if (closing.value) {
            transaction.close()
            throw CancellationException("Datastore closing")
        }
        activeTransactions.update { it + transaction }
        if (closing.value) {
            activeTransactions.update { it - transaction }
            transaction.close()
            throw CancellationException("Datastore closing")
        }
    }
}

private class TrackedFdbFuture<T>(
    private val delegate: FdbFuture<T>,
    private val cleanup: () -> Unit,
) : FdbFuture<T> {
    private val cleaned = atomic(false)

    override suspend fun await(): T = try {
        delegate.await()
    } finally {
        cleanOnce()
    }

    override fun cancel() {
        try {
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

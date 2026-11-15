package maryk.datastore.foundationdb

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import maryk.foundationdb.FutureCancellation
import maryk.foundationdb.asFdbFuture
import maryk.foundationdb.fdbFuture
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction

internal fun <T> CoroutineFdbFuture(deferred: Deferred<T>): FdbFuture<T> =
    deferred.asFdbFuture(FutureCancellation.PROPAGATE)

internal fun <T> foundationDbFuture(block: suspend () -> T): FdbFuture<T> = fdbFuture(block)

internal suspend fun <T> TrackingTransactionContext.runSuspending(
    context: FoundationDBTransactionContext,
    block: suspend (Transaction) -> T,
): T = runSuspendingConfigured(context, block)

internal suspend fun <T> TrackingTransactionContext.runSuspendingThroughAsync(
    context: FoundationDBTransactionContext,
    block: suspend (Transaction) -> T,
): T = supervisorScope {
    val future = runAsyncConfigured(context) { transaction ->
        CoroutineFdbFuture(async { block(transaction) })
    }
    try {
        future.await()
    } finally {
        if (!future.isDone) future.cancel()
    }
}

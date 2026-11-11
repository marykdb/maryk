package maryk.datastore.foundationdb

import maryk.foundationdb.DatabaseOptions
import maryk.foundationdb.TransactionOptions

/** Limits for one native transaction, including its FoundationDB retry loop. */
data class FoundationDBTransactionPolicy(
    val timeoutMillis: Long = 4_500,
    val retryLimit: Long = 10,
    val maxRetryDelayMillis: Long = 1_000,
    val sizeLimitBytes: Long = 10_000_000,
) {
    init {
        require(timeoutMillis in 1..Int.MAX_VALUE.toLong())
        require(retryLimit in 0..Int.MAX_VALUE.toLong())
        require(maxRetryDelayMillis in 0..Int.MAX_VALUE.toLong())
        require(sizeLimitBytes in 32..10_000_000)
    }

    internal fun applyTo(options: DatabaseOptions) {
        options.setTransactionTimeout(timeoutMillis)
        options.setTransactionRetryLimit(retryLimit)
        options.setTransactionMaxRetryDelay(maxRetryDelayMillis)
        options.setTransactionSizeLimit(sizeLimitBytes)
    }

    internal fun applyTo(options: TransactionOptions) {
        options.setTimeout(timeoutMillis)
        options.setRetryLimit(retryLimit)
        options.setMaxRetryDelay(maxRetryDelayMillis)
        options.setSizeLimit(sizeLimitBytes)
    }
}

enum class FoundationDBTransactionKind { Request, Read, Atomic, Maintenance }

data class FoundationDBTransactionContext(
    val kind: FoundationDBTransactionKind,
    val dataModelId: UInt? = null,
)

/**
 * Request limits become database defaults before [FoundationDBDataStore.open]'s
 * databaseOptionsSetter runs. Maintenance limits override those defaults.
 * The hook runs on every retry attempt, after the policy, and may add tags,
 * tracing or operation-specific limits. Atomic and pinned-read safety limits
 * are enforced after the hook. Hooks must not perform external side effects.
 */
class FoundationDBTransactionConfiguration(
    val requestPolicy: FoundationDBTransactionPolicy = FoundationDBTransactionPolicy(),
    val maintenancePolicy: FoundationDBTransactionPolicy = FoundationDBTransactionPolicy(),
    val optionsSetter: TransactionOptions.(FoundationDBTransactionContext) -> Unit = {},
)

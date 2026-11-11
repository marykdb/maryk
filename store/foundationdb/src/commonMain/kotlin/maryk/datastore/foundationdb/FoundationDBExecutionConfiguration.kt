package maryk.datastore.foundationdb

/** Budgets apply to decoded storage rows, including key/version bytes, before value decoding. */
class FoundationDBExecutionConfiguration(
    val maxRecordReadBytes: Long = 16L * 1024 * 1024,
    val maxRequestReadBytes: Long = 32L * 1024 * 1024,
    val maintenancePartitionBytes: Long = 8L * 1024 * 1024,
    val maintenanceConcurrency: Int = 1,
    val watchFallbackMillis: Long = 30_000,
    /** Conservative staged-work budget; overwritten mutations count. Null preserves native admission. */
    val maxTransactionWorkBytes: Long? = null,
) {
    init {
        require(maxRecordReadBytes > 0 && maxRequestReadBytes >= maxRecordReadBytes)
        require(maintenancePartitionBytes > 0)
        require(maintenanceConcurrency in 1..16)
        require(watchFallbackMillis in 1..Int.MAX_VALUE.toLong())
        require(maxTransactionWorkBytes == null || maxTransactionWorkBytes > 0)
    }
}

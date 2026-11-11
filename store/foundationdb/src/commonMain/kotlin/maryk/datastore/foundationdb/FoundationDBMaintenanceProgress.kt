package maryk.datastore.foundationdb

/** Estimates describe the latest index rebuild; progress is process-local and resets on restart. */
data class FoundationDBMaintenanceProgress(
    val estimatedSourceBytes: Long,
    val partitions: Int,
    val completedPartitions: Int,
)

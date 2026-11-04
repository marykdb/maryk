package maryk.datastore.rocksdb

/** Resource limits are shared by all column families in one store. */
data class RocksDBTuning(
    val blockCacheBytes: Long = 64L * 1024 * 1024,
    val memtableBudgetBytes: Long = 32L * 1024 * 1024,
    val enableStatistics: Boolean = true,
) {
    init {
        require(blockCacheBytes > 0) { "Block cache budget must be positive" }
        require(memtableBudgetBytes > 0) { "Memtable budget must be positive" }
    }
}

/** Approximate engine accounting, not process RSS. Cache reservations can overlap memtable accounting. */
data class RocksDBDiagnostics(
    val blockCacheCapacityBytes: Long,
    val blockCacheUsageBytes: Long,
    val blockCachePinnedBytes: Long,
    val memtableBytes: Long,
    val unflushedMemtableBytes: Long,
    val tableReaderBytes: Long,
    val pendingCompactionBytes: Long,
    val immutableMemtables: Long,
    val writeStopped: Boolean,
    val memtableBudgetManaged: Boolean,
    val counters: Map<String, Long>,
)

/** Copied live-file inventories, sampled separately; concurrent flush/compaction can change them. */
data class RocksDBStorageDiagnostics(
    val liveSstFiles: Int,
    val liveSstBytes: Long,
    val liveSstBytesByLevel: Map<Int, Long>,
    val walFiles: Int,
    val walBytes: Long,
)

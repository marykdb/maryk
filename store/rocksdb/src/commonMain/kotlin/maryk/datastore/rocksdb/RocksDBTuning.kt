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

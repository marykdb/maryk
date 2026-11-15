package maryk.datastore.rocksdb

import maryk.rocksdb.AbstractComparator
import maryk.datastore.shared.MAX_ATOMIC_WORK_BYTES
import maryk.rocksdb.BlockBasedTableConfig
import maryk.rocksdb.BloomFilter
import maryk.rocksdb.Cache
import maryk.rocksdb.ColumnFamilyOptions
import maryk.rocksdb.ComparatorOptions
import maryk.rocksdb.IndexSearchType
import maryk.rocksdb.LRUCache
import maryk.rocksdb.WriteBufferManager
import maryk.rocksdb.loadRocksDBLibrary

internal class RocksDBBlockCache(
    private val tuning: RocksDBTuning = RocksDBTuning(),
    private val tableConfiguration: RocksDBTableConfiguration = RocksDBTableConfiguration(),
    private val manageMemtableBudget: Boolean = true,
) {
    init { loadRocksDBLibrary() }
    internal val cache: Cache = LRUCache(tuning.blockCacheBytes, cacheShardBits(tuning.blockCacheBytes))
    // Write-buffer reservations can exceed a smaller read cache, pinning its
    // entire capacity until flush/history release and preventing SST admission.
    // Keep their accounting separate so imports do not evict the read working set.
    private val memtableAccountingCache: Cache = LRUCache(tuning.memtableBudgetBytes, cacheShardBits(tuning.memtableBudgetBytes))
    internal val writeBufferManager = WriteBufferManager(tuning.memtableBudgetBytes, memtableAccountingCache, false)
    private val filterPolicy = tableConfiguration.bloomFilterBitsPerKey?.let(::BloomFilter)
    private val columnFamilyOptions = mutableListOf<ColumnFamilyOptions>()
    private val comparatorOptions = mutableListOf<ComparatorOptions>()
    private val comparators = mutableListOf<AbstractComparator>()

    fun createColumnFamilyOptions(metadata: Boolean = false, configure: ColumnFamilyOptions.() -> Unit = {}) =
        createOptions(bytewiseComparator = true, metadata, configure)

    fun createVersionedColumnFamilyOptions(keySize: Int, metadata: Boolean = false, configure: ColumnFamilyOptions.() -> Unit = {}) =
        createOptions(bytewiseComparator = false, metadata) {
            setComparator(createVersionedComparator(keySize))
            configure()
        }

    private fun createOptions(bytewiseComparator: Boolean, metadata: Boolean, configure: ColumnFamilyOptions.() -> Unit): ColumnFamilyOptions {
        // Native attempts from the public API are serialized. Retain a margin
        // above bounded atomic work instead of two 64 MiB buffers per family.
        val options = createPlatformColumnFamilyOptions(
            if (manageMemtableBudget) MAX_ATOMIC_WORK_BYTES.toLong() * 4 else null,
        )
        try {
            options.setCompressionType(tableConfiguration.compression)
                .setBottommostCompressionType(tableConfiguration.bottommostCompression)
                .setCompressionPerLevel(tableConfiguration.compressionPerLevel)
            val table = BlockBasedTableConfig().setBlockCache(cache).setCacheIndexAndFilterBlocks(true)
                .setFilterPolicy(filterPolicy)
                // Interpolation/auto are not supported by Maryk's historic comparator.
                .setIndexSearchType(if (bytewiseComparator) tableConfiguration.indexSearchType else IndexSearchType.kBinary)
            tableConfiguration.persistentCache?.let { table.setPersistentCache(it) }
            options.setTableFormatConfig(table)
            if (metadata && manageMemtableBudget) {
                options.setArenaBlockSize((tuning.memtableBudgetBytes / 128).coerceIn(4096L, 256L * 1024))
            }
            options.configure()
            columnFamilyOptions += options
            return options
        } catch (error: Throwable) {
            options.close()
            throw error
        }
    }

    private fun createVersionedComparator(keySize: Int): AbstractComparator {
        val options = ComparatorOptions()
        comparatorOptions += options
        return createPlatformVersionedComparator(options, keySize).also(comparators::add)
    }

    fun close() {
        columnFamilyOptions.forEach { it.close() }
        comparators.forEach { it.close() }
        comparatorOptions.forEach { it.close() }
        writeBufferManager.close()
        memtableAccountingCache.close()
        cache.close()
        filterPolicy?.close()
    }
}

internal expect fun createPlatformBlockCache(): Cache?

/** Leave room for SST metadata and memtable reservations within each cache shard. */
internal fun cacheShardBits(capacity: Long): Int {
    var bits = 0
    while (bits < 6 && (capacity shr (bits + 1)) >= 8L * 1024 * 1024) bits++
    return bits
}

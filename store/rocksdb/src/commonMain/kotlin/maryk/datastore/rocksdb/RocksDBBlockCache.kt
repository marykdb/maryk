package maryk.datastore.rocksdb

import maryk.rocksdb.BlockBasedTableConfig
import maryk.rocksdb.Cache
import maryk.rocksdb.ColumnFamilyOptions
import maryk.rocksdb.ComparatorOptions
import maryk.rocksdb.CompressionType
import maryk.rocksdb.loadRocksDBLibrary
import maryk.rocksdb.LRUCache
import maryk.rocksdb.WriteBufferManager
import maryk.datastore.rocksdb.processors.VersionedComparator

internal class RocksDBBlockCache(private val tuning: RocksDBTuning = RocksDBTuning()) {
    init { loadRocksDBLibrary() }
    internal val cache: Cache = LRUCache(tuning.blockCacheBytes)
    internal val writeBufferManager = WriteBufferManager(tuning.memtableBudgetBytes, cache, false)
    private val columnFamilyOptions = mutableListOf<ColumnFamilyOptions>()
    private val comparatorOptions = mutableListOf<ComparatorOptions>()
    private val comparators = mutableListOf<VersionedComparator>()

    fun createColumnFamilyOptions(configure: ColumnFamilyOptions.() -> Unit = {}) =
        ColumnFamilyOptions().apply {
            setCompressionType(CompressionType.LZ4_COMPRESSION)
            setBottommostCompressionType(CompressionType.ZSTD_COMPRESSION)
            val table = BlockBasedTableConfig().setBlockCache(cache).setCacheIndexAndFilterBlocks(true)
            setTableFormatConfig(table)
            configure()
        }.also(columnFamilyOptions::add)

    fun createVersionedComparator(keySize: Int): VersionedComparator {
        val options = ComparatorOptions()
        comparatorOptions += options
        return VersionedComparator(options, keySize).also(comparators::add)
    }

    fun close() {
        columnFamilyOptions.forEach { it.close() }
        comparators.forEach { it.close() }
        comparatorOptions.forEach { it.close() }
        writeBufferManager.close()
        cache.close()
    }
}

internal expect fun createPlatformBlockCache(): Cache?

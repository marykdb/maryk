package maryk.datastore.rocksdb

import maryk.datastore.rocksdb.processors.VersionedComparator
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
) {
    init { loadRocksDBLibrary() }
    internal val cache: Cache = LRUCache(tuning.blockCacheBytes)
    internal val writeBufferManager = WriteBufferManager(tuning.memtableBudgetBytes, cache, false)
    private val filterPolicy = tableConfiguration.bloomFilterBitsPerKey?.let(::BloomFilter)
    private val columnFamilyOptions = mutableListOf<ColumnFamilyOptions>()
    private val comparatorOptions = mutableListOf<ComparatorOptions>()
    private val comparators = mutableListOf<VersionedComparator>()

    fun createColumnFamilyOptions(configure: ColumnFamilyOptions.() -> Unit = {}) =
        createOptions(bytewiseComparator = true, configure)

    fun createVersionedColumnFamilyOptions(keySize: Int, configure: ColumnFamilyOptions.() -> Unit = {}) =
        createOptions(bytewiseComparator = false) {
            setComparator(createVersionedComparator(keySize))
            configure()
        }

    private fun createOptions(bytewiseComparator: Boolean, configure: ColumnFamilyOptions.() -> Unit): ColumnFamilyOptions {
        val options = ColumnFamilyOptions()
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
            options.configure()
            columnFamilyOptions += options
            return options
        } catch (error: Throwable) {
            options.close()
            throw error
        }
    }

    private fun createVersionedComparator(keySize: Int): VersionedComparator {
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
        filterPolicy?.close()
    }
}

internal expect fun createPlatformBlockCache(): Cache?

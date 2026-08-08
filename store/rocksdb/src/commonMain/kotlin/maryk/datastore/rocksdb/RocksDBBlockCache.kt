package maryk.datastore.rocksdb

import maryk.rocksdb.BlockBasedTableConfig
import maryk.rocksdb.Cache
import maryk.rocksdb.ColumnFamilyOptions
import maryk.rocksdb.ComparatorOptions
import maryk.datastore.rocksdb.processors.VersionedComparator

internal class RocksDBBlockCache {
    private val cache = createPlatformBlockCache()
    private val columnFamilyOptions = mutableListOf<ColumnFamilyOptions>()
    private val comparatorOptions = mutableListOf<ComparatorOptions>()
    private val comparators = mutableListOf<VersionedComparator>()

    fun createColumnFamilyOptions(configure: ColumnFamilyOptions.() -> Unit = {}) =
        ColumnFamilyOptions().apply {
            cache?.let {
                setTableFormatConfig(BlockBasedTableConfig().setBlockCache(it))
            }
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
        cache?.close()
    }
}

internal expect fun createPlatformBlockCache(): Cache?

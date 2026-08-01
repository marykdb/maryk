package maryk.datastore.rocksdb

import maryk.rocksdb.BlockBasedTableConfig
import maryk.rocksdb.Cache
import maryk.rocksdb.ColumnFamilyOptions

internal class RocksDBBlockCache {
    private val cache = createPlatformBlockCache()

    fun createColumnFamilyOptions(configure: ColumnFamilyOptions.() -> Unit = {}) =
        ColumnFamilyOptions().apply {
            cache?.let {
                setTableFormatConfig(BlockBasedTableConfig().setBlockCache(it))
            }
            configure()
        }

    fun close() {
        cache?.close()
    }
}

internal expect fun createPlatformBlockCache(): Cache?

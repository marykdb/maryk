package maryk.datastore.rocksdb

import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.rocksdb.BottommostLevelCompaction
import maryk.rocksdb.BlockBasedTableConfig
import maryk.rocksdb.CompactRangeOptions
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ColumnFamilyOptions
import maryk.rocksdb.CompressionType
import maryk.rocksdb.DBOptions
import maryk.rocksdb.FlushOptions
import maryk.rocksdb.RocksDB
import maryk.rocksdb.defaultColumnFamily
import maryk.rocksdb.loadRocksDBLibrary
import maryk.rocksdb.openRocksDB
import maryk.rocksdb.tableProperties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class RocksDBCompressionTest {
    @Test
    fun readsOldSstFilesAlongsideNewCompressionAndAfterCompaction() {
        loadRocksDBLibrary()
        for (oldCodec in listOf(CompressionType.SNAPPY_COMPRESSION, CompressionType.NO_COMPRESSION)) {
            val folder = createTestDBFolder("compression-compatibility-${oldCodec.name}")
            try {
                val legacyCache = createPlatformBlockCache()
                try {
                    val table = BlockBasedTableConfig().setFormatVersion(5)
                    legacyCache?.let { table.setBlockCache(it) }
                    ColumnFamilyOptions().setCompressionType(oldCodec).setDisableAutoCompactions(true)
                        .setNumLevels(2).setTableFormatConfig(table).use { oldOptions ->
                            withDatabase(folder, oldOptions) { db, family ->
                                repeat(128) { index -> db.put(family, key(index), value(index)) }
                                FlushOptions().setWaitForFlush(true).use { db.flush(it, family) }
                                val expected = if (oldCodec == CompressionType.SNAPPY_COMPRESSION) "snappy" else "nocompression"
                                assertTrue(db.tableProperties(family).values.any {
                                    it.compressionName()?.replace("_", "")?.contains(expected, ignoreCase = true) == true
                                }, "The compatibility fixture must contain SSTs using $oldCodec")
                            }
                        }
                } finally {
                    legacyCache?.close()
                }
                val optionsOwner = RocksDBBlockCache()
                try {
                    val options = optionsOwner.createColumnFamilyOptions {
                        setDisableAutoCompactions(true)
                        // Two levels let this small fixture reach the bottommost level.
                        setNumLevels(2)
                    }
                    withDatabase(folder, options) { db, family ->
                        repeat(128) { index -> assertContentEquals(value(index), db.get(family, key(index))) }
                        repeat(128) { index -> db.put(family, key(index + 128), value(index + 128)) }
                        FlushOptions().setWaitForFlush(true).use { db.flush(it, family) }
                        assertTrue(db.tableProperties(family).values.any {
                            hasCodec(it.compressionName(), "lz4", "04")
                        }, "New flushes must use LZ4")
                        repeat(256) { index -> assertContentEquals(value(index), db.get(family, key(index))) }
                        val compaction = CompactRangeOptions()
                            .setBottommostLevelCompaction(BottommostLevelCompaction.kForce)
                        try {
                            db.compactRange(family, byteArrayOf(), byteArrayOf(-1), compaction)
                        } finally {
                            // The common binding omits AutoCloseable; both platform implementations own a closeable handle.
                            (compaction as AutoCloseable).close()
                        }
                        assertTrue(db.tableProperties(family).values.any {
                            hasCodec(it.compressionName(), "zstd", "07")
                        }, "Bottommost compaction must use Zstandard: ${db.tableProperties(family).values.map { it.compressionName() }}")
                    }
                    withDatabase(folder, options) { db, family ->
                        repeat(256) { index -> assertContentEquals(value(index), db.get(family, key(index))) }
                    }
                } finally {
                    optionsOwner.close()
                }
            } finally {
                deleteFolder(folder)
            }
        }
    }

    private fun <T> withDatabase(path: String, options: ColumnFamilyOptions, block: (RocksDB, ColumnFamilyHandle) -> T): T =
        DBOptions().setCreateIfMissing(true).use { dbOptions ->
            val handles = mutableListOf<ColumnFamilyHandle>()
            val db = openRocksDB(dbOptions, path, listOf(ColumnFamilyDescriptor(defaultColumnFamily, options)), handles)
            try {
                block(db, handles.single())
            } finally {
                handles.forEach { it.close() }
                db.close()
            }
        }

    // Format 7 stores <manager>;<hex codec ids>;, while legacy SSTs use a readable name.
    private fun hasCodec(metadata: String?, name: String, code: String): Boolean =
        metadata?.contains(name, ignoreCase = true) == true ||
            metadata?.split(';')?.getOrNull(1)?.chunked(2)?.contains(code) == true

    private fun key(index: Int) = "compression-${index.toString().padStart(4, '0')}".encodeToByteArray()
    private fun value(index: Int) = "value-$index:${"Maryk compression compatibility ".repeat(128)}".encodeToByteArray()
}

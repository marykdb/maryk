package maryk.datastore.rocksdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.change
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ColumnFamilyOptions
import maryk.rocksdb.CompressionType
import maryk.rocksdb.DBOptions
import maryk.rocksdb.FlushOptions
import maryk.rocksdb.IndexSearchType
import maryk.rocksdb.InfoLogLevel
import maryk.rocksdb.LRUCache
import maryk.rocksdb.PersistentCache
import maryk.rocksdb.RocksDB
import maryk.rocksdb.Statistics
import maryk.rocksdb.TickerType
import maryk.rocksdb.createLogger
import maryk.rocksdb.defaultColumnFamily
import maryk.rocksdb.getDefaultEnv
import maryk.rocksdb.loadRocksDBLibrary
import maryk.rocksdb.openRocksDB
import maryk.rocksdb.setLogger
import maryk.rocksdb.tableProperties
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RocksDBTableConfigurationTest {
    @Test
    fun defaultsReadUnfilteredSstsAndNewFiltersRemainReadableWhenDisabled() {
        val path = createTestDBFolder("table-filter-upgrade")
        val key = "existing".encodeToByteArray()
        val value = byteArrayOf(42)
        try {
            for ((stage, filtered) in listOf(false, true, false).withIndex()) {
                val owner = RocksDBBlockCache(tableConfiguration = if (filtered) RocksDBTableConfiguration()
                    else RocksDBTableConfiguration(bloomFilterBitsPerKey = null, indexSearchType = IndexSearchType.kBinary))
                try {
                    withDatabase(path, owner.createColumnFamilyOptions { setDisableAutoCompactions(true) }) { db, family ->
                        if (db.get(family, key) == null) {
                            db.put(family, key, value)
                            FlushOptions().setWaitForFlush(true).use { db.flush(it, family) }
                            assertTrue(db.tableProperties(family).values.all { it.filterSize() == 0L })
                        }
                        assertContentEquals(value, db.get(family, key))
                        if (filtered) {
                            db.put(family, "new".encodeToByteArray(), value)
                            FlushOptions().setWaitForFlush(true).use { db.flush(it, family) }
                            assertTrue(db.tableProperties(family).values.any { it.filterSize() > 0 })
                        }
                        if (stage > 0) assertContentEquals(value, db.get(family, "new".encodeToByteArray()))
                        assertNull(db.get(family, "absent".encodeToByteArray()))
                    }
                } finally { owner.close() }
            }
        } finally { deleteFolder(path) }
    }

    @Test
    fun perLevelCompressionOverridesTheGeneralCodec() {
        val path = createTestDBFolder("table-compression-override")
        val owner = RocksDBBlockCache(tableConfiguration = RocksDBTableConfiguration(
            compression = CompressionType.ZSTD_COMPRESSION,
            compressionPerLevel = List(7) { CompressionType.NO_COMPRESSION },
        ))
        try {
            withDatabase(path, owner.createColumnFamilyOptions()) { db, family ->
                val value = ByteArray(32768) { 42 }
                db.put(family, byteArrayOf(1), value)
                FlushOptions().setWaitForFlush(true).use { db.flush(it, family) }
                assertTrue(db.tableProperties(family).values.single().dataSize() >= value.size)
                assertContentEquals(value, db.get(family, byteArrayOf(1)))
            }
        } finally { owner.close(); deleteFolder(path) }
    }

    @Test
    fun optionalCachesAndLoggerSurviveStoreCloseAndReopen() = runTest {
        loadRocksDBLibrary()
        val path = createTestDBFolder("optional-table-settings")
        val cachePath = createTestDBFolder("optional-persistent-cache")
        val messages = atomic(0)
        try {
            createLogger(InfoLogLevel.INFO_LEVEL) { _, message -> if (message.isNotEmpty()) messages.incrementAndGet() }.use { logger ->
                PersistentCache(getDefaultEnv(), cachePath, 128L * 1024 * 1024, logger, false).use { persistent ->
                    LRUCache(4L * 1024 * 1024).use { rows ->
                        Statistics().use { statistics ->
                            DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true)
                                .setRowCache(rows).setLogger(logger).setStatistics(statistics).use { options ->
                                    val config = RocksDBTableConfiguration(
                                        indexSearchType = IndexSearchType.kInterpolation, persistentCache = persistent,
                                    )
                                    val value = Log("optional APIs")
                                    val key = Log.key(value)
                                    var snapshot = 0uL
                                    var store = RocksDBDataStore.open(tableConfiguration = config, relativePath = path,
                                        dataModelsById = mapOf(1u to Log), rocksDBOptions = options)
                                    try {
                                        store.execute(Log.add(value))
                                        snapshot = store.captureSnapshotVersion()
                                        store.execute(Log.change(key.change(Change(Log.ref { message } with "changed"))))
                                        FlushOptions().setWaitForFlush(true).use { store.db.flush(it) }
                                    } finally { store.close() }
                                    assertTrue(persistent.isOwningHandle())
                                    assertTrue(options.isOwningHandle())
                                    store = RocksDBDataStore.open(tableConfiguration = config, relativePath = path,
                                        dataModelsById = mapOf(1u to Log), rocksDBOptions = options)
                                    try {
                                        repeat(5) { assertEquals("changed", store.execute(Log.get(key)).values.single().values { message }) }
                                        assertEquals("optional APIs", store.execute(Log.get(key, toVersion = snapshot)).values.single().values { message })
                                        store.verifyStorageChecksums()
                                    } finally { store.close() }
                                    assertTrue(statistics.getTickerCount(TickerType.ROW_CACHE_HIT) > 0)
                                    assertTrue(statistics.getTickerCount(TickerType.PERSISTENT_CACHE_MISS) > 0)
                                    assertTrue(messages.value > 0)
                                }
                        }
                    }
                }
            }
        } finally { deleteFolder(path); deleteFolder(cachePath) }
    }

    @Test
    fun invalidBloomBudgetsAreRejectedBeforeOpening() {
        for (bits in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { RocksDBTableConfiguration(bloomFilterBitsPerKey = bits) }
        }
    }

    private fun withDatabase(path: String, options: ColumnFamilyOptions, block: (RocksDB, ColumnFamilyHandle) -> Unit) {
        DBOptions().setCreateIfMissing(true).use { dbOptions ->
            val handles = mutableListOf<ColumnFamilyHandle>()
            val db = openRocksDB(dbOptions, path, listOf(ColumnFamilyDescriptor(defaultColumnFamily, options)), handles)
            try { block(db, handles.single()) } finally { handles.forEach { it.close() }; db.close() }
        }
    }
}

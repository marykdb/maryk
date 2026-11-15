package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object CacheIsolationModel : RootDataModel<CacheIsolationModel>() {
    val body by string(1u)
}

class MemtableCacheIsolationTest {
    @Test
    fun unflushedWritesDoNotPinTheReadCache() = runTest {
        val folder = createTestDBFolder("memtable-cache-isolation")
        val tuning = RocksDBTuning(blockCacheBytes = 64L * 1024, memtableBudgetBytes = 8L * 1024 * 1024)
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to CacheIsolationModel), tuning = tuning)
        try {
            val body = "x".repeat(4096)
            val first = assertIs<AddSuccess<CacheIsolationModel>>(store.execute(CacheIsolationModel.add(
                CacheIsolationModel.create { CacheIsolationModel.body with body },
            )).statuses.single())
            repeat(64) { index ->
                store.execute(CacheIsolationModel.add(CacheIsolationModel.create { CacheIsolationModel.body with "$index$body" }))
            }
            val memory = store.diagnostics()
            assertTrue(memory.memtableBytes > tuning.blockCacheBytes)
            assertTrue(memory.blockCachePinnedBytes < tuning.blockCacheBytes,
                "Memtables must not reserve the read cache: $memory")
            assertEquals(body, store.execute(CacheIsolationModel.get(first.key)).values.single().values { CacheIsolationModel.body })
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

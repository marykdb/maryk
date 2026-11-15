package maryk.datastore.rocksdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class RocksDBReadContextTest {
    @Test
    fun concurrentSequentialOptionsKeepTheCapturedSnapshotAndClose() = runTest {
        val folder = createTestDBFolder("read-context-options")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
        try {
            val family = store.getColumnFamilies(1u).keys
            val key = ByteArray(Log.Meta.keyByteSize)
            store.db.put(family, key, byteArrayOf(1))
            val context = store.createReadContext()
            val options = try {
                store.db.put(family, key, byteArrayOf(2))
                val ready = CompletableDeferred<Unit>()
                val options = coroutineScope {
                    val readers = List(16) { async(Dispatchers.Default) { ready.await(); context.sequentialReadOptions } }
                    ready.complete(Unit)
                    readers.awaitAll()
                }
                options.forEach {
                    assertSame(options.first(), it)
                    assertSame(context.defaultReadOptions, it)
                    assertContentEquals(byteArrayOf(1), store.db.get(family, it, key))
                }
                assertContentEquals(byteArrayOf(1), store.db.get(family, context.defaultReadOptions, key))
                options
            } finally { context.close() }
            options.forEach { assertFalse(it.isOwningHandle()) }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

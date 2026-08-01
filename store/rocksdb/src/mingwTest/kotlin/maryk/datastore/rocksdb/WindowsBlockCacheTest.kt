package maryk.datastore.rocksdb

import kotlin.test.Test
import kotlin.test.assertIs
import maryk.rocksdb.LRUCache

class WindowsBlockCacheTest {
    @Test
    fun usesLruCacheInsteadOfAutoHyperClockCache() {
        val cache = createPlatformBlockCache()

        try {
            assertIs<LRUCache>(cache)
        } finally {
            cache?.close()
        }
    }
}

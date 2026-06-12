package maryk.datastore.rocksdb.processors.helpers

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.StorageException
import maryk.core.models.key
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.LAST_VERSION_INDICATOR
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GetLastVersionTest {
    @Test
    fun rejectsStoredLastVersionWithoutVersionPrefix() = runTest {
        val folder = createTestDBFolder("last-version-missing-version")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val columnFamilies = store.getColumnFamilies(SimpleMarykModel)
            val key = SimpleMarykModel.key(
                SimpleMarykModel.create {
                    value with "haha"
                }
            )
            store.db.put(columnFamilies.table, key.bytes + LAST_VERSION_INDICATOR, byteArrayOf(1))

            DBAccessor(store).use { dbAccessor ->
                assertFailsWith<StorageException> {
                    getLastVersion(dbAccessor, columnFamilies, store.defaultReadOptions, key)
                }
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

package maryk.datastore.rocksdb.processors.helpers

import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse

class GetKeyByUniqueValueTest {
    @Test
    fun currentUniqueValueIgnoresValueWithoutVersionPrefix() = runTest {
        val folder = createTestDBFolder("unique-value-missing-version")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )

        try {
            val columnFamilies = store.getColumnFamilies(CompleteMarykModel)
            val reference = byteArrayOf(1, 2, 3)
            store.db.put(columnFamilies.unique, reference, byteArrayOf(4))

            var processed = false
            DBAccessor(store).use { dbAccessor ->
                getKeyByUniqueValue(
                    dbAccessor = dbAccessor,
                    columnFamilies = columnFamilies,
                    readOptions = store.defaultReadOptions,
                    reference = reference,
                    keySize = CompleteMarykModel.Meta.keyByteSize,
                    toVersion = null,
                ) { _, _ ->
                    processed = true
                }
            }

            assertFalse(processed)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

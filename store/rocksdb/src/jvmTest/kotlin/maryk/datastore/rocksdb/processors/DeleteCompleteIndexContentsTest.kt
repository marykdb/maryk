package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.helpers.createHistoricIndexKey
import maryk.datastore.rocksdb.processors.helpers.createIndexKey
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.deleteFolder
import maryk.test.models.AnyValueSetIndexModel
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeleteCompleteIndexContentsTest {
    @Test
    fun deleteCompleteIndexContentsClearsHistoricIndexFamily() = runTest {
        val folder = createTestDBFolder("delete-complete-index-contents")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            )

            val indexable = AnyValueSetIndexModel { setValues.refToAny() }
            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(AnyValueSetIndexModel)
            )

            val currentIndexKey = createIndexKey(indexable.referenceStorageByteArray.bytes, byteArrayOf(1, 2, 3))
            val historicIndexKey = createHistoricIndexKey(
                indexable.referenceStorageByteArray.bytes,
                byteArrayOf(1, 2, 3),
                1uL.toReversedVersionBytes()
            )

            store.db.put(columnFamilies.index, currentIndexKey, byteArrayOf(1))
            store.db.put(columnFamilies.historic.index, historicIndexKey, byteArrayOf(0))

            assertNotNull(store.db.get(columnFamilies.index, currentIndexKey))
            assertNotNull(store.db.get(columnFamilies.historic.index, historicIndexKey))

            Transaction(store).use { transaction ->
                deleteCompleteIndexContents(transaction, columnFamilies, indexable)
                transaction.commit()
            }

            assertNull(store.db.get(columnFamilies.index, currentIndexKey))
            assertNull(store.db.get(columnFamilies.historic.index, historicIndexKey))

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}

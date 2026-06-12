package maryk.datastore.rocksdb.processors.helpers

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.assertNull

class IterateValuesTest {
    @Test
    fun currentIterateValuesStopsAtReferencePrefix() = runTest {
        val folder = createTestDBFolder("iterate-values-current-prefix")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val columnFamilies = store.getColumnFamilies(CompleteMarykModel)
            val key = ByteArray(CompleteMarykModel.Meta.keyByteSize) { (it + 1).toByte() }
            val reference = key + byteArrayOf(5)
            val unrelatedReference = key + byteArrayOf(6)
            val storedValue = HLC.toStorageBytes(HLC(10uL)) + byteArrayOf(42)

            store.db.put(columnFamilies.table, unrelatedReference, storedValue)

            DBAccessor(store).use { dbAccessor ->
                val found = dbAccessor.iterateValues(
                    columnFamilies = columnFamilies,
                    readOptions = store.defaultReadOptions,
                    toVersion = null,
                    keyLength = key.size,
                    reference = reference,
                ) { _, _, _, _, _, _ -> true }

                assertNull(found)
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun historicIterateValuesStopsAtReferencePrefix() = runTest {
        val folder = createTestDBFolder("iterate-values-historic-prefix")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val columnFamilies = store.getColumnFamilies(CompleteMarykModel) as HistoricTableColumnFamilies
            val key = ByteArray(CompleteMarykModel.Meta.keyByteSize) { (it + 1).toByte() }
            val reference = key + byteArrayOf(5)
            val unrelatedReference = key + byteArrayOf(6)
            val version = 10uL

            store.db.put(
                columnFamilies.historic.table,
                unrelatedReference + version.toReversedVersionBytes(),
                byteArrayOf(42)
            )

            DBAccessor(store).use { dbAccessor ->
                val found = dbAccessor.iterateValues(
                    columnFamilies = columnFamilies,
                    readOptions = store.defaultReadOptions,
                    toVersion = version,
                    keyLength = key.size,
                    reference = reference,
                ) { _, _, _, _, _, _ -> true }

                assertNull(found)
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

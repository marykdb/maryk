package maryk.datastore.rocksdb.processors.helpers

import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertContentEquals

class HistoricalTableReaderOrderingTest {
    @Test
    fun readerSeeksBackFromNestedQualifierToParentQualifier() = runTest {
        val folder = createTestDBFolder("historic-reader-ordering")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = dataModelsForTests,
            )
            val key = Log.key(Log("historic-reader-ordering"))
            val version = 1uL
            val versionBytes = version.toReversedVersionBytes()
            val columnFamilies = store.getColumnFamilies(Log) as HistoricTableColumnFamilies

            store.db.put(columnFamilies.historic.table, key.bytes + versionBytes, byteArrayOf(1))
            store.db.put(columnFamilies.historic.table, key.bytes + byteArrayOf(-2) + versionBytes, byteArrayOf(2))
            store.db.put(columnFamilies.historic.table, key.bytes + byteArrayOf(-1) + versionBytes, byteArrayOf(3))

            DBAccessor(store).use { accessor ->
                HistoricalTableReader(accessor, columnFamilies, store.defaultReadOptions, version).use { reader ->
                    assertContentEquals(byteArrayOf(2), reader.getValue(key.bytes + byteArrayOf(-2)) { value, offset, length ->
                        value.copyOfRange(offset, offset + length)
                    })
                    assertContentEquals(byteArrayOf(1), reader.getValue(key.bytes) { value, offset, length ->
                        value.copyOfRange(offset, offset + length)
                    })
                }
            }

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}

package maryk.datastore.rocksdb.processors.helpers

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.invert
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GetValueTest {
    @Test
    fun currentGetValueRejectsMissingVersionPrefix() = runTest {
        val folder = createTestDBFolder("get-value-missing-version")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val columnFamilies = store.getColumnFamilies(CompleteMarykModel)
            val keyAndReference = ByteArray(CompleteMarykModel.Meta.keyByteSize) { (it + 1).toByte() } + byteArrayOf(5)

            store.db.put(columnFamilies.table, keyAndReference, byteArrayOf(1))

            DBAccessor(store).use { dbAccessor ->
                assertFailsWith<StorageException> {
                    dbAccessor.getValue(
                        columnFamilies = columnFamilies,
                        readOptions = store.defaultReadOptions,
                        toVersion = null,
                        keyAndReference = keyAndReference,
                    ) { value, offset, length ->
                        value.copyOfRange(offset, offset + length)
                    }
                }
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun historicGetValueIgnoresPrefixCollidingReference() = runTest {
        val folder = createTestDBFolder("get-value-prefix-collision")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val columnFamilies = store.getColumnFamilies(CompleteMarykModel) as HistoricTableColumnFamilies
            val key = ByteArray(CompleteMarykModel.Meta.keyByteSize) { (it + 1).toByte() }
            val reference = byteArrayOf(5)
            val collidingReference = byteArrayOf(5, 6)
            val version = 10uL
            val versionBytes = HLC.toStorageBytes(HLC(version))
            val collidingPayload = byteArrayOf(42)
            val exactPayload = byteArrayOf(7)

            store.db.put(
                columnFamilies.historic.table,
                (key + collidingReference + versionBytes).also { it.invert(it.size - versionBytes.size) },
                collidingPayload
            )

            DBAccessor(store).use { dbAccessor ->
                val missing = dbAccessor.getValue(
                    columnFamilies = columnFamilies,
                    readOptions = store.defaultReadOptions,
                    toVersion = version,
                    keyAndReference = key + reference,
                ) { value, offset, length ->
                    value.copyOfRange(offset, offset + length)
                }
                assertNull(missing)
            }

            store.db.put(
                columnFamilies.historic.table,
                (key + reference + versionBytes).also { it.invert(it.size - versionBytes.size) },
                exactPayload
            )

            DBAccessor(store).use { dbAccessor ->
                val exact = dbAccessor.getValue(
                    columnFamilies = columnFamilies,
                    readOptions = store.defaultReadOptions,
                    toVersion = version,
                    keyAndReference = key + reference,
                ) { value, offset, length ->
                    value.copyOfRange(offset, offset + length)
                }
                assertContentEquals(exactPayload, exact)
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

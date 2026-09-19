package maryk.datastore.rocksdb.processors.helpers

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.extensions.bytes.invert
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun historicIterateValuesIgnoresTooShortPrefixMatchingRow() = runTest {
        val folder = createTestDBFolder("iterate-values-short-historic-row")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val columnFamilies = store.getColumnFamilies(CompleteMarykModel) as HistoricTableColumnFamilies
            val key = ByteArray(CompleteMarykModel.Meta.keyByteSize) { (it + 1).toByte() }
            val reference = key + byteArrayOf(5)
            val version = 10uL
            val payload = byteArrayOf(42)
            val versionBytes = HLC.toStorageBytes(HLC(version))

            store.db.put(
                columnFamilies.historic.table,
                key + byteArrayOf(5, version.toReversedVersionBytes().first()),
                byteArrayOf(99)
            )
            store.db.put(
                columnFamilies.historic.table,
                (reference + versionBytes).also { it.invert(it.size - versionBytes.size) },
                payload
            )

            DBAccessor(store).use { dbAccessor ->
                val found = dbAccessor.iterateValues(
                    columnFamilies = columnFamilies,
                    readOptions = store.defaultReadOptions,
                    toVersion = version,
                    keyLength = key.size,
                    reference = reference,
                ) { _, _, _, value, offset, length ->
                    value.copyOfRange(offset, offset + length)
                }

                assertEquals(payload.toList(), found?.toList())
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun historicIterateValuesDoesNotReturnOlderValueAfterDeleteMarker() = runTest {
        val folder = createTestDBFolder("iterate-values-historic-delete-marker")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val columnFamilies = store.getColumnFamilies(CompleteMarykModel) as HistoricTableColumnFamilies
            val key = ByteArray(CompleteMarykModel.Meta.keyByteSize) { (it + 1).toByte() }
            val reference = key + byteArrayOf(5)
            val oldVersion = 10uL
            val deleteVersion = 20uL
            val oldVersionBytes = HLC.toStorageBytes(HLC(oldVersion))
            val deleteVersionBytes = HLC.toStorageBytes(HLC(deleteVersion))

            store.db.put(
                columnFamilies.historic.table,
                (reference + oldVersionBytes).also { it.invert(it.size - oldVersionBytes.size) },
                byteArrayOf(42)
            )
            store.db.put(
                columnFamilies.historic.table,
                (reference + deleteVersionBytes).also { it.invert(it.size - deleteVersionBytes.size) },
                TypeIndicator.DeletedIndicator.byteArray
            )

            DBAccessor(store).use { dbAccessor ->
                val found = dbAccessor.iterateValues(
                    columnFamilies = columnFamilies,
                    readOptions = store.defaultReadOptions,
                    toVersion = deleteVersion,
                    keyLength = key.size,
                    reference = reference,
                ) { _, _, _, value, offset, length ->
                    value.copyOfRange(offset, offset + length)
                }

                assertNull(found)
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

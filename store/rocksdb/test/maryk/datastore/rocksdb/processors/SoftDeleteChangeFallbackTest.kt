package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SoftDeleteChangeFallbackTest {
    @Test
    fun ignoresSoftDeleteValueWithoutDeleteFlag() = runTest {
        val folder = createTestDBFolder("soft-delete-missing-flag")
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
            val version = 5uL
            store.db.put(
                columnFamilies.table,
                key.bytes + SOFT_DELETE_INDICATOR,
                HLC.toStorageBytes(HLC(version))
            )

            DBAccessor(store).use { dbAccessor ->
                val objectChange = DataObjectVersionedChange(key, changes = emptyList())
                assertEquals(
                    objectChange,
                    addSoftDeleteChangeIfMissing(
                        dbAccessor = dbAccessor,
                        columnFamilies = columnFamilies,
                        readOptions = store.defaultReadOptions,
                        key = key,
                        fromVersion = version,
                        objectChange = objectChange,
                    )
                )
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun ignoresOverlongSoftDeleteValueForDeletionChecks() = runTest {
        val folder = createTestDBFolder("soft-delete-overlong")
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
            store.db.put(
                columnFamilies.table,
                key.bytes + SOFT_DELETE_INDICATOR,
                HLC.toStorageBytes(HLC(5uL)) + byteArrayOf(TRUE, FALSE)
            )

            DBAccessor(store).use { dbAccessor ->
                assertFalse(
                    isSoftDeleted(
                        dbAccessor = dbAccessor,
                        columnFamilies = columnFamilies,
                        readOptions = store.defaultReadOptions,
                        toVersion = null,
                        key = key.bytes
                    )
                )
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

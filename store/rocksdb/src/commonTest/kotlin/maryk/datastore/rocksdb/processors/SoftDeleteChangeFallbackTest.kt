package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.exceptions.StorageException
import maryk.core.models.key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SoftDeleteChangeFallbackTest {
    @Test
    fun rejectsSoftDeleteValueWithoutDeleteFlag() = runTest {
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
                assertFailsWith<StorageException> {
                    addSoftDeleteChangeIfMissing(
                        dbAccessor = dbAccessor,
                        columnFamilies = columnFamilies,
                        readOptions = store.defaultReadOptions,
                        key = key,
                        fromVersion = version,
                        objectChange = DataObjectVersionedChange(key, changes = emptyList()),
                    )
                }
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

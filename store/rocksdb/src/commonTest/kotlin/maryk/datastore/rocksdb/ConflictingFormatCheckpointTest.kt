package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.migration.MigrationException
import maryk.createTestDBFolder
import maryk.datastore.shared.migration.StorageFormatUpgradeStateKey
import maryk.deleteFolder
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ConflictingFormatCheckpointTest {
    @Test
    fun currentFormatDoesNotIgnoreAnUnfinishedCheckpointOnReopen() = runTest {
        val path = createTestDBFolder("conflicting-format-checkpoint")
        try {
            val store = RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to SimpleMarykModel))
            try {
                store.db.put(store.storageUpgradeHandles.first(), StorageFormatUpgradeStateKey, byteArrayOf(1))
            } finally {
                store.close()
            }
            assertFailsWith<MigrationException> {
                RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to SimpleMarykModel)).close()
            }
        } finally {
            deleteFolder(path)
        }
    }
}

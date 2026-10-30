package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.migration.MigrationException
import maryk.datastore.shared.migration.StorageFormatUpgradeStateKey
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ConflictingFormatCheckpointTest {
    @Test
    fun currentFormatDoesNotIgnoreAnUnfinishedCheckpointOnReopen() = runTest {
        val path = listOf("maryk", "conflicting-format-checkpoint-${Random.nextInt()}")
        suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = path, dataModelsById = mapOf(1u to SimpleMarykModel))
        val store = open()
        try {
            store.tc.run { it.set(store.storageFormatPrefix + StorageFormatUpgradeStateKey, byteArrayOf(1)) }
        } finally {
            store.close()
        }
        assertFailsWith<MigrationException> { open().close() }
    }
}

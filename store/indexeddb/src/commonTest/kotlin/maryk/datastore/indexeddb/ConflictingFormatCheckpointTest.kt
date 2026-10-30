package maryk.datastore.indexeddb

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
        installIndexedDbForTests()
        val name = "conflicting-format-checkpoint-${Random.nextInt()}"
        suspend fun open() = IndexedDbDataStore.open(name, mapOf(1u to SimpleMarykModel))
        val store = open()
        try {
            store.byteStore.put("meta", StorageFormatUpgradeStateKey, byteArrayOf(1))
        } finally {
            store.close()
        }
        assertFailsWith<MigrationException> { open().close() }
    }
}

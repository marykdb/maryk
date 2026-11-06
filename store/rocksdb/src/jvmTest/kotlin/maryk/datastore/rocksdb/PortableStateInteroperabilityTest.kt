package maryk.datastore.rocksdb

import maryk.datastore.test.advancedStateBackupModels
import maryk.datastore.test.assertAdvancedStateRoundTrip
import maryk.datastore.test.assertDeletedStateAllocations
import maryk.datastore.test.stateBackupTestEncryptionProvider

import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.datastore.memory.InMemoryDataStore
import maryk.deleteFolder
import kotlin.test.Test

class PortableStateInteroperabilityTest {
    @Test
    fun transfersBetweenMemoryAndRocksDbWithDifferentModelIds() = runTest {
        for (rocksDbIsSource in listOf(false, true)) {
            val path = createTestDBFolder("state-portable-$rocksDbIsSource")
            val rocksDb = RocksDBDataStore.open(relativePath = path, dataModelsById = advancedStateBackupModels, keepAllVersions = false, fieldEncryptionProvider = stateBackupTestEncryptionProvider(3))
            val memory = InMemoryDataStore.open(dataModelsById = advancedStateBackupModels.mapKeys { it.key + 10u })
            try {
                val (source, target) = if (rocksDbIsSource) rocksDb to memory else memory to rocksDb
                assertAdvancedStateRoundTrip(source, target)
                assertDeletedStateAllocations(target)
            } finally {
                rocksDb.close()
                memory.close()
                deleteFolder(path)
            }
        }
    }
}

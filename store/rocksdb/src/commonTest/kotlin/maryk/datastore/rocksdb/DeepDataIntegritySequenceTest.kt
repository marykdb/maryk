package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.datastore.test.DeepDataIntegritySequence
import maryk.datastore.test.DeepIntegrityRecord
import kotlin.test.Test

class DeepDataIntegritySequenceTest {
    @Test
    fun preservesCurrentHistoryOwnershipAndPortableReplay() = runTest {
        val sourceFolder = createTestDBFolder("deep-integrity-source")
        val targetFolder = createTestDBFolder("deep-integrity-target")
        val models = mapOf(1u to DeepIntegrityRecord)
        val source = RocksDBDataStore.open(relativePath = sourceFolder, dataModelsById = models, keepAllVersions = true)
        val target = RocksDBDataStore.open(relativePath = targetFolder, dataModelsById = models, keepAllVersions = true)
        try {
            DeepDataIntegritySequence(source, target).preservesCurrentHistoryOwnershipAndPortableReplay()
        } finally {
            source.close()
            target.close()
            deleteFolder(sourceFolder)
            deleteFolder(targetFolder)
        }
    }
}

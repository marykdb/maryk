package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.DeepDataIntegritySequence
import maryk.datastore.test.DeepIntegrityRecord
import kotlin.random.Random
import kotlin.test.Test

class DeepDataIntegritySequenceTest {
    @Test
    fun preservesCurrentHistoryOwnershipAndPortableReplay() = runTest {
        val id = Random.nextInt()
        val models = mapOf(1u to DeepIntegrityRecord)
        val source = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "deep-integrity-source-$id"), dataModelsById = models, keepAllVersions = true)
        val target = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "deep-integrity-target-$id"), dataModelsById = models, keepAllVersions = true)
        try {
            DeepDataIntegritySequence(source, target).preservesCurrentHistoryOwnershipAndPortableReplay()
        } finally {
            source.close()
            target.close()
        }
    }
}

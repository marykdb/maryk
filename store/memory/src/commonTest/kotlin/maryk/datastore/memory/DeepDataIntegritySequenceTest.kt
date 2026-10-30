package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.DeepDataIntegritySequence
import maryk.datastore.test.DeepIntegrityRecord
import kotlin.test.Test

class DeepDataIntegritySequenceTest {
    @Test
    fun preservesCurrentHistoryOwnershipAndPortableReplay() = runTest {
        val models = mapOf(1u to DeepIntegrityRecord)
        val source = InMemoryDataStore.open(dataModelsById = models, keepAllVersions = true)
        val target = InMemoryDataStore.open(dataModelsById = models, keepAllVersions = true)
        try {
            DeepDataIntegritySequence(source, target).preservesCurrentHistoryOwnershipAndPortableReplay()
        } finally {
            source.close()
            target.close()
        }
    }
}

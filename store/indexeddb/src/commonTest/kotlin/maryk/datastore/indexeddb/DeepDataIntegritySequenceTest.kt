package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.DeepDataIntegritySequence
import maryk.datastore.test.DeepIntegrityRecord
import kotlin.random.Random
import kotlin.test.Test

class DeepDataIntegritySequenceTest {
    @Test
    fun preservesCurrentHistoryOwnershipAndPortableReplay() = runTest {
        installIndexedDbForTests()
        val id = Random.nextInt()
        val models = mapOf(1u to DeepIntegrityRecord)
        val source = IndexedDbDataStore.open("deep-integrity-source-$id", models, keepAllVersions = true)
        val target = IndexedDbDataStore.open("deep-integrity-target-$id", models, keepAllVersions = true)
        try {
            DeepDataIntegritySequence(source, target).preservesCurrentHistoryOwnershipAndPortableReplay()
        } finally {
            source.close()
            target.close()
        }
    }
}

package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.StatefulIntegrityRecord
import maryk.datastore.test.assertStatefulIntegrity
import kotlin.random.Random
import kotlin.test.Test

class StatefulIntegrityConformanceTest {
    @Test
    fun generatedSequencesRetainTheirIndependentStateAndHistory() = runTest {
        installIndexedDbForTests()
        val id = Random.nextInt()
        assertStatefulIntegrity(
            open = { label -> IndexedDbDataStore.open("stateful-$id-$label", mapOf(1u to StatefulIntegrityRecord), keepAllVersions = true) },
            supportsReopen = true,
        )
    }
}

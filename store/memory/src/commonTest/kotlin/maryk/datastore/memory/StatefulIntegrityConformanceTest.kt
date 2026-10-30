package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.StatefulIntegrityRecord
import maryk.datastore.test.assertStatefulIntegrity
import kotlin.test.Test

class StatefulIntegrityConformanceTest {
    @Test
    fun generatedSequencesRetainTheirIndependentStateAndHistory() = runTest {
        assertStatefulIntegrity(
            open = { InMemoryDataStore.open(dataModelsById = mapOf(1u to StatefulIntegrityRecord), keepAllVersions = true) },
            supportsReopen = false,
        )
    }
}

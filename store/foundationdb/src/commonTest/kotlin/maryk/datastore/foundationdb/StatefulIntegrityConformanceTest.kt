package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.StatefulIntegrityRecord
import maryk.datastore.test.assertStatefulIntegrity
import kotlin.random.Random
import kotlin.test.Test

class StatefulIntegrityConformanceTest {
    @Test
    fun generatedSequencesRetainTheirIndependentStateAndHistory() = runTest {
        val id = Random.nextInt()
        assertStatefulIntegrity(
            open = { label -> FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "stateful-$id-$label"),
                dataModelsById = mapOf(1u to StatefulIntegrityRecord), keepAllVersions = true,
            ) },
            supportsReopen = true,
        )
    }
}

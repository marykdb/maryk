package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.datastore.test.StatefulIntegrityRecord
import maryk.datastore.test.assertStatefulIntegrity
import kotlin.test.Test

class StatefulIntegrityConformanceTest {
    @Test
    fun generatedSequencesRetainTheirIndependentStateAndHistory() = runTest {
        val folders = mutableMapOf<String, String>()
        try {
            assertStatefulIntegrity(
                open = { label -> RocksDBDataStore.open(
                    relativePath = folders.getOrPut(label) { createTestDBFolder("stateful-$label") },
                    dataModelsById = mapOf(1u to StatefulIntegrityRecord), keepAllVersions = true,
                ) },
                supportsReopen = true,
            )
        } finally {
            folders.values.forEach(::deleteFolder)
        }
    }
}

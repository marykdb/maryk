package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.AtomicInterruptionEncryptionProvider
import maryk.datastore.test.assertAtomicEncryptionFailureRollsBack
import maryk.datastore.test.interruptedAtomicModels
import kotlin.random.Random
import kotlin.test.Test

class AtomicInterruptionTest {
    @Test
    fun encryptionFailureAfterEarlierAtomicWritesRollsBack() = runTest {
        val provider = AtomicInterruptionEncryptionProvider()
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "atomic-encryption-interruption-${Random.nextInt()}"),
            dataModelsById = interruptedAtomicModels, keepAllVersions = true, keepUpdateHistoryIndex = true,
            fieldEncryptionProvider = provider)
        try {
            assertAtomicEncryptionFailureRollsBack(store, provider)
        } finally {
            store.close()
        }
    }
}

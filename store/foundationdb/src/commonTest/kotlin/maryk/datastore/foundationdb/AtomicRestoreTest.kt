package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.atomicRestoreModels
import maryk.datastore.test.checkAtomicRestore
import maryk.datastore.test.checkAtomicRestoreSwap
import maryk.datastore.test.checkInvalidAtomicRestore
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.random.Random

class AtomicRestoreTest {
    @Test
    fun restoresReceiverFirstAcrossChunks() = runTest(timeout = 3.minutes) {
        runCase("receiver-first") { source, target -> checkAtomicRestore(source, target, receiverFirst = true, batchSize = 1u) }
    }
    @Test
    fun restoresOwnerFirst() = runTest(timeout = 3.minutes) {
        runCase("owner-first") { source, target -> checkAtomicRestore(source, target, receiverFirst = false, batchSize = 2u) }
    }
    @Test
    fun restoresCyclicUniqueTransfer() = runTest(timeout = 3.minutes) {
        runCase("swap", ::checkAtomicRestoreSwap)
    }
    @Test
    fun invalidGroupRollsBack() = runTest(timeout = 3.minutes) {
        runCase("invalid", ::checkInvalidAtomicRestore)
    }
    private suspend fun runCase(name: String, check: suspend (IsDataStore, IsDataStore) -> Unit) {
        suspend fun open(suffix: String) = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "atomic-restore-$name-$suffix-${Random.nextInt()}"), dataModelsById = atomicRestoreModels, keepAllVersions = true, keepUpdateHistoryIndex = true)
        val source = open("source")
        val target = open("target")
        try { check(source, target) } finally {
            source.close()
            target.close()
        }
    }
}

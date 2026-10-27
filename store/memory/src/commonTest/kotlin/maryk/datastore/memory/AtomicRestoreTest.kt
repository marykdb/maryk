package maryk.datastore.memory

import maryk.datastore.test.AtomicRestoreBackup
import maryk.datastore.shared.DataStoreRestoreOptions
import maryk.datastore.shared.backup
import maryk.datastore.shared.restore
import maryk.core.exceptions.RequestException
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.test.models.SimpleMarykModel
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.atomicRestoreModels
import maryk.datastore.test.checkAtomicRestore
import maryk.datastore.test.checkAtomicRestoreSwap
import maryk.datastore.test.checkInvalidAtomicRestore
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

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
    @Test
    fun oversizedLateVersionGroupFailsBeforeAnyReplay() = runTest {
        runCase("oversized-group") { source, target ->
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha early" }))
            source.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with "ha ${"x".repeat(400)}" },
                SimpleMarykModel.create { value with "ha ${"y".repeat(400)}" },
            ))
            val backup = AtomicRestoreBackup()
            source.backup(backup, batchSize = 1u)
            assertFailsWith<RequestException> { target.restore(backup, true, DataStoreRestoreOptions(maxReplayBytes = 700)) }
            assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        }
    }

    private suspend fun runCase(name: String, check: suspend (IsDataStore, IsDataStore) -> Unit) {
        suspend fun open() = InMemoryDataStore.open(dataModelsById = atomicRestoreModels, keepAllVersions = true, keepUpdateHistoryIndex = true)
        val source = open()
        val target = open()
        try { check(source, target) } finally {
            source.close()
            target.close()
        }
    }
}

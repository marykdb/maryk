package maryk.datastore.rocksdb

import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.datastore.shared.backup
import maryk.datastore.shared.restore
import maryk.datastore.test.AtomicRestoreBackup
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.atomicRestoreModels
import maryk.datastore.test.checkAtomicRestore
import maryk.datastore.test.checkAtomicRestoreSwap
import maryk.datastore.test.checkInvalidAtomicRestore
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import maryk.createTestDBFolder
import maryk.deleteFolder

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
        runCase("swap", check = ::checkAtomicRestoreSwap)
    }
    @Test
    fun invalidGroupRollsBack() = runTest(timeout = 3.minutes) {
        runCase("invalid", check = ::checkInvalidAtomicRestore)
    }
    @Test
    fun preservesExistingFloatingPointUniqueLookupBytes() = runTest {
        runCase("float-unique", models = mapOf(1u to FloatingUniqueRestoreModel)) { source, target ->
            val numbers = listOf(Double.fromBits(0x7ff8000000000001L), Double.fromBits(0x7ff8000000000002L))
            source.execute(FloatingUniqueRestoreModel.add(*numbers.map { value -> FloatingUniqueRestoreModel.create { number with value } }.toTypedArray()))
            val backup = AtomicRestoreBackup()
            source.backup(backup)
            target.restore(backup)
            val actual = target.execute(FloatingUniqueRestoreModel.scan(allowTableScan = true)).values
                .map { it.values { number }!!.toRawBits() }.sorted()
            assertEquals(numbers.map { it.toRawBits() }.sorted(), actual)
        }
    }

    private suspend fun runCase(name: String, models: Map<UInt, IsRootDataModel> = atomicRestoreModels, check: suspend (IsDataStore, IsDataStore) -> Unit) {
        val sourceFolder = createTestDBFolder("atomic-restore-source-$name")
        val targetFolder = createTestDBFolder("atomic-restore-target-$name")
        val source = RocksDBDataStore.open(relativePath = sourceFolder, dataModelsById = models, keepAllVersions = true, keepUpdateHistoryIndex = true)
        val target = RocksDBDataStore.open(relativePath = targetFolder, dataModelsById = models, keepAllVersions = true, keepUpdateHistoryIndex = true)
        try { check(source, target) } finally {
            source.close()
            target.close()
            deleteFolder(sourceFolder)
            deleteFolder(targetFolder)
        }
    }
}

private object FloatingUniqueRestoreModel : RootDataModel<FloatingUniqueRestoreModel>() {
    val number by number(1u, type = Float64, unique = true)
}

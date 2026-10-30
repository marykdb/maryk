package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.LaggedSnapshotRecord
import maryk.datastore.test.assertLaggedRefillPosition
import maryk.datastore.test.assertSameKeyRefillPreservesChanges
import maryk.datastore.test.assertLaggedSnapshotFlowMatrix
import maryk.datastore.test.assertLaggedUpdateHistorySnapshotOrder
import maryk.datastore.test.assertUpdateHistoryRevisionTies
import maryk.datastore.test.assertUpdateHistoryRefillUsesCurrentSnapshot
import kotlin.test.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

class LaggedSnapshotFlowTest {
    private suspend fun withStore(history: Boolean, block: suspend (IsDataStore) -> Unit) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "lagged-snapshot-$history-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to LaggedSnapshotRecord), keepAllVersions = history, keepUpdateHistoryIndex = history,
        )
        try {
            block(store)
        } finally {
            store.close()
        }
    }

    @Test
    fun snapshotsDoNotReplayAlreadyIncludedDeltas() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) withStore(history, ::assertLaggedSnapshotFlowMatrix)
    }

    @Test
    fun historySnapshotsRetainActualVersionOrder() = runTest(timeout = 3.minutes) {
        for (projected in listOf(false, true)) withStore(true) {
            assertLaggedUpdateHistorySnapshotOrder(it, projected)
        }
    }

    @Test
    fun historyRefillUsesCurrentValuesAndVersion() = runTest(timeout = 3.minutes) {
        for (projected in listOf(false, true)) withStore(true) {
            assertUpdateHistoryRefillUsesCurrentSnapshot(it, projected)
        }
    }

    @Test
    fun refillPreservesCandidateOrder() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) for (indexed in listOf(false, true)) for (projected in listOf(false, true)) {
            withStore(history) { assertLaggedRefillPosition(it, indexed, projected) }
        }
    }

    @Test
    fun batchRevisionTiesHaveStableHistoryOrder() = runTest(timeout = 3.minutes) {
        for (descendingInput in listOf(false, true)) for (projected in listOf(false, true)) for (limit in listOf(1u, 2u)) {
            withStore(true) { assertUpdateHistoryRevisionTies(it, descendingInput, projected, limit) }
        }
    }
    @Test
    fun sameKeyRefillPreservesInterveningChanges() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) for (indexed in listOf(false, true)) for (projected in listOf(false, true)) {
            withStore(history) { assertSameKeyRefillPreservesChanges(it, indexed, false, projected) }
        }
        for (projected in listOf(false, true)) {
            withStore(true) { assertSameKeyRefillPreservesChanges(it, false, true, projected) }
        }
    }

}

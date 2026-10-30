package maryk.datastore.shared.migration

import kotlinx.coroutines.test.runTest
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.StorageFormat
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.RootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.migration.convertLegacyStorageQualifier
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.types.numeric.Float64
import maryk.datastore.test.legacyFloatBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object NanQualifierModel : RootDataModel<NanQualifierModel>() {
    val values by map(1u, keyDefinition = NumberDefinition(type = Float64), valueDefinition = StringDefinition())
}

class StorageFormatUpgradeTest {
    private class Interrupted : RuntimeException()

    @Test
    fun currentStampCannotHideAnUnfinishedCheckpoint() = runTest {
        val store = Store().apply { seed() }
        store.commit(listOf(
            StorageFormatWrite(0, StorageFormatMetadataKey, StorageFormat.CURRENT.toString().encodeToByteArray()),
            StorageFormatWrite(0, StorageFormatUpgradeStateKey, byteArrayOf(1)),
        ))
        val commits = store.commits
        assertFailsWith<MigrationException> { store.upgradeStorageFormat() }
        assertEquals(commits, store.commits)
        assertEquals(0, store.scans)
    }

    @Test
    fun outOfRangeExpandCursorCannotSkipConversion() = runTest {
        val store = Store().apply { seed() }
        val state = MigrationState("storage:1->2:${store.identity}", MigrationPhase.Expand,
            MigrationStateStatus.Running, 1u, "1", "2", ByteArray(12).also { it[3] = 3 })
        store.commit(listOf(StorageFormatWrite(0, StorageFormatUpgradeStateKey, state.toPersistedBytes())))
        val commits = store.commits
        assertFailsWith<MigrationException> { store.upgradeStorageFormat() }
        assertEquals(commits, store.commits)
        assertEquals(0, store.scans)
        assertContentEquals(byteArrayOf(10), store.get(1, byteArrayOf(0)))
        assertContentEquals(byteArrayOf(11), store.get(1, byteArrayOf(1)))
    }

    @Test
    fun missingStagedEntryCannotLoseASwappedSourceValue() = runTest {
        val store = Store().apply { seed(); failAfter = 1 }
        assertFailsWith<Interrupted> { store.upgradeStorageFormat() }
        store.failAfter = Int.MAX_VALUE
        val prefix = "maryk.storage/stage/".encodeToByteArray()
        val staged = store.scan(0, prefix, 64u)
            .filter { it.key.copyOfRange(0, minOf(it.key.size, prefix.size)).contentEquals(prefix) }
        assertEquals(2, staged.size)
        store.commit(listOf(StorageFormatWrite(0, staged.last().key, null)))
        val commits = store.commits
        assertFailsWith<MigrationException> { store.upgradeStorageFormat() }
        assertEquals(commits, store.commits)
        assertContentEquals(byteArrayOf(10), store.get(1, byteArrayOf(0)))
        assertContentEquals(byteArrayOf(11), store.get(1, byteArrayOf(1)))
    }

    @Test
    fun missingStagedTailIsRejectedAfterEveryPhaseTransition() = runTest {
        val completed = Store().apply { seed() }
        completed.upgradeStorageFormat()
        val phases = mutableSetOf<MigrationPhase>()
        for (boundary in 1 until completed.commits) {
            val store = Store().apply { seed(); failAfter = boundary }
            assertFailsWith<Interrupted> { store.upgradeStorageFormat() }
            store.failAfter = Int.MAX_VALUE
            val state = MigrationState.requireFromPersistedBytes(store.get(0, StorageFormatUpgradeStateKey)!!)
            if (state.phase == MigrationPhase.Expand || state.message == "cleanup") continue
            phases += state.phase
            val prefix = "maryk.storage/stage/".encodeToByteArray()
            val staged = store.scan(0, prefix, 64u).filter { it.key.decodeToString().startsWith("maryk.storage/stage/") }
            assertEquals(2, staged.size)
            val source = store.data.mapValues { it.value.second.toHexString() }
            store.commit(listOf(StorageFormatWrite(0, staged.last().key, null)))
            val commits = store.commits
            assertFailsWith<MigrationException>("Commit $boundary, phase ${state.phase}") { store.upgradeStorageFormat() }
            assertEquals(commits, store.commits)
            assertEquals(source, store.data.mapValues { it.value.second.toHexString() })
        }
        assertEquals(setOf(MigrationPhase.Backfill, MigrationPhase.Verify, MigrationPhase.Contract), phases)
    }

    @Test
    fun invalidStagedNamespaceCannotRedirectWritesIntoMetadata() = runTest {
        val store = Store().apply { seed(); failAfter = 3 }
        assertFailsWith<Interrupted> { store.upgradeStorageFormat() }
        store.failAfter = Int.MAX_VALUE
        val prefix = "maryk.storage/stage/".encodeToByteArray()
        val staged = store.scan(0, prefix, 1u).single()
        val malformed = staged.value.copyOf().also { bytes -> repeat(4) { bytes[it] = 0 } }
        store.commit(listOf(StorageFormatWrite(0, staged.key, malformed)))
        val commits = store.commits
        assertFailsWith<MigrationException> { store.upgradeStorageFormat() }
        assertEquals(commits, store.commits)
        assertContentEquals(byteArrayOf(10), store.get(1, byteArrayOf(0)))
        assertContentEquals(byteArrayOf(11), store.get(1, byteArrayOf(1)))
    }

    @Test
    fun invalidBackfillCursorCannotLeaveOldIdentitiesBehind() = runTest {
        val store = Store().apply { seed(); data.remove("01"); failAfter = 3 }
        assertFailsWith<Interrupted> { store.upgradeStorageFormat() }
        store.failAfter = Int.MAX_VALUE
        val state = MigrationState.requireFromPersistedBytes(store.get(0, StorageFormatUpgradeStateKey)!!)
        assertEquals(MigrationPhase.Backfill, state.phase)
        store.commit(listOf(StorageFormatWrite(0, StorageFormatUpgradeStateKey,
            state.copy(cursor = "maryk.storage/target/invalid".encodeToByteArray()).toPersistedBytes())))
        val commits = store.commits
        assertFailsWith<MigrationException> { store.upgradeStorageFormat() }
        assertEquals(commits, store.commits)
        assertContentEquals(byteArrayOf(10), store.get(1, byteArrayOf(0)))
        assertEquals(null, store.get(1, byteArrayOf(1)))
    }

    private class MultiNamespaceStore : StorageFormatUpgradeStore {
        override val namespaceCount = 3
        override val identity = "cross-batch-models-and-history"
        val tables = List(namespaceCount + 1) { mutableMapOf<String, StorageFormatRow>() }
        val writesByNamespace = mutableMapOf<Int, Int>()
        var commits = 0
        var failAtCommit = Int.MAX_VALUE
        var failBeforeCommit = false
        var failDuringPublication = false
        var publishedSchemas = 0
        var collideInLastBatch = false

        init {
            for (namespace in 1..namespaceCount) {
                repeat(145) { index ->
                    val key = byteArrayOf(index.toByte())
                    tables[namespace][key.toHexString()] = StorageFormatRow(key, byteArrayOf(namespace.toByte(), index.toByte()))
                }
            }
            val token = StorageFormatTokenPrefix + byteArrayOf(1)
            tables[0][token.toHexString()] = StorageFormatRow(token, byteArrayOf(42))
        }

        override suspend fun get(namespace: Int, key: ByteArray) = tables[namespace][key.toHexString()]?.value
        override suspend fun scan(namespace: Int, after: ByteArray?, limit: UInt) =
            tables[namespace].values.sortedBy { it.key.toHexString() }
                .filter { after == null || it.key.toHexString() > after.toHexString() }
                .take(limit.toInt())

        override suspend fun convert(namespace: Int, row: StorageFormatRow): StorageFormatRow {
            val index = row.key.single().toUByte().toInt()
            if (collideInLastBatch && namespace == namespaceCount && index == 127) {
                return StorageFormatRow(byteArrayOf(67), row.value)
            }
            // Move entries across the 64-row checkpoints, preserving unrelated cells.
            return if (index < 128) StorageFormatRow(byteArrayOf(((index + 67) % 128).toByte()), row.value) else row
        }

        override suspend fun commit(writes: List<StorageFormatWrite>) {
            commits++
            if (commits == failAtCommit && failBeforeCommit) throw Interrupted()
            for (write in writes) {
                val value = write.value
                if (value == null) tables[write.namespace].remove(write.key.toHexString())
                else tables[write.namespace][write.key.toHexString()] = StorageFormatRow(write.key, value)
                writesByNamespace[write.namespace] = (writesByNamespace[write.namespace] ?: 0) + 1
            }
            if (commits == failAtCommit) throw Interrupted()
        }

        override suspend fun preparePublication() {
            // Model schema pairs can publish separately before the global format stamp.
            while (publishedSchemas < namespaceCount) {
                publishedSchemas++
                if (failDuringPublication) throw Interrupted()
            }
        }

        fun snapshot() = tables.map { table -> table.mapValues { it.value.value.toHexString() } }
    }

    @Test
    fun lateCrossBatchCollisionLeavesEverySourceNamespaceIntact() = runTest {
        val store = MultiNamespaceStore().apply { collideInLastBatch = true }
        val original = store.snapshot().drop(1)
        assertFailsWith<MigrationException> { store.upgradeStorageFormat() }
        assertEquals(original, store.snapshot().drop(1))
        assertEquals(StorageFormat.LEGACY, store.readStorageFormat())
        assertTrue((1..store.namespaceCount).none { it in store.writesByNamespace })
    }

    @Test
    fun resumesCrossBatchMovesAcrossModelsAndHistoryBeforeAndAfterEveryCommit() = runTest {
        val completed = MultiNamespaceStore()
        completed.upgradeStorageFormat()
        val expected = completed.snapshot()
        for (before in listOf(false, true)) {
            for (boundary in 1..completed.commits) {
                val store = MultiNamespaceStore().apply {
                    failAtCommit = boundary
                    failBeforeCommit = before
                }
                assertFailsWith<Interrupted> { store.upgradeStorageFormat() }
                store.failAtCommit = Int.MAX_VALUE
                store.upgradeStorageFormat()
                assertEquals(expected, store.snapshot(), "Commit $boundary, before=$before")
                for (namespace in 1..store.namespaceCount) {
                    // The 17 unchanged cells never participate in removal or installation.
                    assertEquals(256, store.writesByNamespace[namespace])
                }
                assertEquals(store.namespaceCount, store.publishedSchemas)
                assertTrue(store.tables[0].values.none { it.key.decodeToString().startsWith("maryk.storage/token/") })
            }
        }
    }

    @Test
    fun resumesAfterEverySourceSchemaPublicationBeforeGlobalStamp() = runTest {
        val store = MultiNamespaceStore().apply { failDuringPublication = true }
        repeat(store.namespaceCount) { expectedPublished ->
            assertFailsWith<Interrupted> { store.upgradeStorageFormat() }
            assertEquals(expectedPublished + 1, store.publishedSchemas)
            assertEquals(StorageFormat.LEGACY, store.readStorageFormat())
        }
        store.failDuringPublication = false
        store.upgradeStorageFormat()
        assertEquals(StorageFormat.CURRENT, store.readStorageFormat())
        val expected = MultiNamespaceStore().also { it.upgradeStorageFormat() }
        assertEquals(expected.snapshot(), store.snapshot())
    }
    private class Store(private val qualifierModel: IsValuesDataModel? = null) : StorageFormatUpgradeStore {
        override val namespaceCount = 1
        override var identity = "test-index"
        val metadata = mutableMapOf<String, Pair<ByteArray, ByteArray>>()
        val data = mutableMapOf<String, Pair<ByteArray, ByteArray>>()
        var failAfter = Int.MAX_VALUE
        var commits = 0
        var scans = 0
        var preparations = 0
        var collision = false
        private fun table(namespace: Int) = if (namespace == 0) metadata else data
        override suspend fun get(namespace: Int, key: ByteArray) = table(namespace)[key.toHexString()]?.second
        override suspend fun scan(namespace: Int, after: ByteArray?, limit: UInt): List<StorageFormatRow> {
            scans++
            return table(namespace).values.sortedBy { it.first.toHexString() }.filter { after == null || it.first.toHexString() > after.toHexString() }
                .take(limit.toInt()).map { StorageFormatRow(it.first, it.second) }
        }
        override suspend fun commit(writes: List<StorageFormatWrite>) {
            for (write in writes) {
                val value = write.value
                if (value == null) table(write.namespace).remove(write.key.toHexString())
                else table(write.namespace)[write.key.toHexString()] = write.key to value
            }
            if (++commits == failAfter) throw Interrupted()
        }
        override suspend fun convert(namespace: Int, row: StorageFormatRow) = qualifierModel?.let {
            StorageFormatRow(it.convertLegacyStorageQualifier(row.key).bytes, row.value)
        } ?: if (row.key[0] == 9.toByte()) row else StorageFormatRow(byteArrayOf((if (collision) 3 else row.key[0].toInt() xor 1).toByte()), row.value)
        override suspend fun preparePublication() { preparations++ }
        fun seed() {
            for (key in listOf(0, 1, 9)) data[byteArrayOf(key.toByte()).toHexString()] = byteArrayOf(key.toByte()) to byteArrayOf((key + 10).toByte())
        }
    }

    @Test
    fun olderUnreleasedBackfillCheckpointCannotBypassANewPreflightPolicy() = runTest {
        val store = Store().apply { seed() }
        val previous = MigrationState("storage:1->2:${store.identity}", MigrationPhase.Backfill,
            MigrationStateStatus.Running, 1u, "1", "2")
        store.commit(listOf(StorageFormatWrite(0, StorageFormatUpgradeStateKey, previous.toPersistedBytes())))
        store.identity += ",collection-count-proof-v1"
        val original = store.data.mapValues { it.value.second.toHexString() }
        assertFailsWith<MigrationException> { store.upgradeStorageFormat() }
        assertEquals(original, store.data.mapValues { it.value.second.toHexString() })
        assertEquals(0, store.scans)
    }

    @Test
    fun collidingLegacyNanCollectionKeysFailBeforeAnySourceRowsMove() = runTest {
        val store = Store(NanQualifierModel)
        val prefix = NanQualifierModel.values.ref().toStorageByteArray() + byteArrayOf(8)
        val first = prefix + legacyFloatBytes(Double.fromBits(0x7ff8000000000001L))
        val second = prefix + legacyFloatBytes(Double.NaN)
        store.data[first.toHexString()] = first to byteArrayOf(10)
        store.data[second.toHexString()] = second to byteArrayOf(11)
        assertFailsWith<MigrationException> { store.upgradeStorageFormat() }
        assertContentEquals(byteArrayOf(10), store.get(1, first))
        assertContentEquals(byteArrayOf(11), store.get(1, second))
        assertEquals(StorageFormat.LEGACY, store.readStorageFormat())
    }

    @Test
    fun resumesAfterEveryCommittedBoundaryWithoutReapplyingConvertedEntries() = runTest {
        val baseline = Store().apply { seed() }
        baseline.upgradeStorageFormat()
        for (boundary in 1..baseline.commits) {
            val store = Store().apply { seed(); failAfter = boundary }
            assertFailsWith<Interrupted> { store.upgradeStorageFormat() }
            store.failAfter = Int.MAX_VALUE
            store.upgradeStorageFormat()
            assertContentEquals(byteArrayOf(11), store.get(1, byteArrayOf(0)))
            assertContentEquals(byteArrayOf(10), store.get(1, byteArrayOf(1)))
            assertContentEquals(byteArrayOf(19), store.get(1, byteArrayOf(9)))
            assertEquals(StorageFormat.CURRENT, store.readStorageFormat())
            val scans = store.scans
            store.upgradeStorageFormat()
            assertEquals(scans, store.scans)
        }
    }

    @Test
    fun collisionFailsBeforeAnySourceRowsAreChanged() = runTest {
        val store = Store().apply { seed(); collision = true }
        assertFailsWith<MigrationException> { store.upgradeStorageFormat() }
        assertContentEquals(byteArrayOf(10), store.get(1, byteArrayOf(0)))
        assertContentEquals(byteArrayOf(11), store.get(1, byteArrayOf(1)))
        assertEquals(1, store.readStorageFormat())
    }

    @Test
    fun unknownFutureFormatIsRejectedWithoutScanningData() = runTest {
        val store = Store().apply { seed() }
        store.commit(listOf(StorageFormatWrite(0, StorageFormatMetadataKey, "3".encodeToByteArray())))
        assertFailsWith<MigrationException> { store.upgradeStorageFormat() }
        assertEquals(0, store.scans)
    }
}

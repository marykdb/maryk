package maryk.datastore.shared.migration

import kotlinx.coroutines.test.runTest
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.StorageFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StorageFormatUpgradeTest {
    private class Interrupted : RuntimeException()
    private class Store : StorageFormatUpgradeStore {
        override val namespaceCount = 1
        override val identity = "test-index"
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
        override suspend fun convert(namespace: Int, row: StorageFormatRow) =
            if (row.key[0] == 9.toByte()) row else StorageFormatRow(byteArrayOf((if (collision) 3 else row.key[0].toInt() xor 1).toByte()), row.value)
        override suspend fun preparePublication() { preparations++ }
        fun seed() {
            for (key in listOf(0, 1, 9)) data[byteArrayOf(key.toByte()).toHexString()] = byteArrayOf(key.toByte()) to byteArrayOf((key + 10).toByte())
        }
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

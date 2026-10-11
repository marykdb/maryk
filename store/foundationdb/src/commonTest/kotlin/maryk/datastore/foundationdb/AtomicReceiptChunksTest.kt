package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.foundationdb.processors.helpers.unwrapFdb
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.test.dataModelsForTests
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import maryk.foundationdb.Range
import maryk.foundationdb.tuple.Tuple
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class AtomicReceiptChunksTest {
    @Test
    fun largeReceiptsCommitReplayAndRemainReadableAfterReopen() = runTest {
        val name = "atomic-chunks-${Random.nextInt()}"
        val directory = listOf("maryk", name)
        val prefix = Tuple.from(name).pack()
        val receipt = ByteArray(200_037) { (it % 251).toByte() }
        suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory, dataModelsById = dataModelsForTests)
        var store = open()
        try {
            store.tc.run { transaction -> writeAtomicReceipt(transaction, prefix, "a", receipt) }
            store.close()
            store = open()
            val replay = store.tc.run { transaction ->
                readAtomicReceipt(transaction, prefix, "a")
            }
            assertContentEquals(receipt, replay)
            val small = byteArrayOf(1, 2, 3)
            store.tc.run { transaction -> writeAtomicReceipt(transaction, prefix, "b", small) }
            assertContentEquals(small, store.tc.run { transaction -> readAtomicReceipt(transaction, prefix, "b") })
        } finally {
            store.tc.run { transaction -> transaction.clear(Range.startsWith(prefix)) }
            store.close()
        }
    }

    @Test
    fun abandonedNativeTransactionPublishesNeitherManifestNorChunks() = runTest {
        val name = "atomic-chunks-rollback-${Random.nextInt()}"
        val prefix = Tuple.from(name).pack()
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", name), dataModelsById = dataModelsForTests)
        try {
            val failure = assertFailsWith<Exception> {
                store.tc.run { transaction ->
                    writeAtomicReceipt(transaction, prefix, "a", ByteArray(200_000))
                    error("Injected failure before native commit")
                }
            }
            assertIs<IllegalStateException>(failure.unwrapFdb())
            assertNull(store.tc.run { transaction -> readAtomicReceipt(transaction, prefix, "a") })
            assertNull(store.tc.run { transaction -> transaction.get(prefix + Tuple.from("a", 0L).pack()).awaitResult() })
        } finally { store.close() }
    }
}

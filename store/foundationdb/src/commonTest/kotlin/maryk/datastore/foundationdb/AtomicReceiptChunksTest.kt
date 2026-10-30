package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.StorageException
import maryk.datastore.foundationdb.processors.helpers.unwrapFdb
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.shared.MAX_ATOMIC_BYTES
import maryk.datastore.shared.tagReleasedTransactionReceipt
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import maryk.foundationdb.Range
import maryk.foundationdb.tuple.Tuple
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AtomicReceiptChunksTest {
    @Test
    fun receiptLengthLimitIncludesCurrentHeaderAndAddsReleasedHeaderOnlyOnce() = runTest {
        val name = "atomic-chunks-limit-${Random.nextInt()}"
        val prefix = Tuple.from(name).pack()
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", name), dataModelsById = dataModelsForTests)
        val released = ByteArray(MAX_ATOMIC_BYTES + 32) { (it % 251).toByte() }
        val header = tagReleasedTransactionReceipt(byteArrayOf())
        val current = ByteArray(released.size + header.size) { (it % 239).toByte() }
        try {
            store.tc.run { transaction ->
                writeAtomicReceipt(transaction, prefix, "current", current)
                writeAtomicReceipt(transaction, prefix, "released", released)
                val manifestKey = prefix + Tuple.from("released").pack()
                transaction.set(manifestKey, header + transaction.get(manifestKey).awaitResult()!!)
            }
            assertContentEquals(current, store.tc.run { readAtomicReceipt(it, prefix, "current") })
            assertContentEquals(tagReleasedTransactionReceipt(released), store.tc.run { readAtomicReceipt(it, prefix, "released") })
            for ((id, manifest) in listOf("oversized-current" to current.size + 1, "oversized-released" to released.size + 1)) {
                store.tc.run { transaction ->
                    val length = ByteArray(4) { index -> (manifest ushr (24 - index * 8)).toByte() }
                    transaction.set(prefix + Tuple.from(id).pack(), if (id == "oversized-released") header + length else length)
                }
                val failure = assertFailsWith<Exception> { store.tc.run { readAtomicReceipt(it, prefix, id) } }
                assertEquals("Invalid atomic receipt length", assertIs<StorageException>(failure.unwrapFdb()).message)
            }
        } finally {
            store.tc.run { it.clear(Range.startsWith(prefix)) }
            store.close()
        }
    }

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

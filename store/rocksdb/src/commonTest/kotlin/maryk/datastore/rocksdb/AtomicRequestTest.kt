package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runTransactionDataStoreTests
import maryk.datastore.test.runAtomicDataStoreTests
import maryk.datastore.test.createAtomicReceiptForReopen
import maryk.datastore.test.createTransactionReceiptForReopen
import maryk.datastore.shared.executeTransaction
import maryk.datastore.shared.executeAtomic
import kotlin.test.assertEquals
import maryk.datastore.shared.supportsAtomicRequests
import kotlin.test.assertTrue
import kotlin.test.Test
import maryk.createTestDBFolder
import maryk.deleteFolder

class AtomicRequestTest {
    @Test
    fun sharedAtomicContract() = runTest {
        val folder = createTestDBFolder("atomic")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = dataModelsForTests, keepAllVersions = true, keepUpdateHistoryIndex = true)
        try { assertTrue(store.supportsAtomicRequests); runAtomicDataStoreTests(store); runTransactionDataStoreTests(store) } finally { store.close(); deleteFolder(folder) }
    }
    @Test
    fun receiptSurvivesReopen() = runTest {
        val folder = createTestDBFolder("atomic-receipt")
        suspend fun open() = RocksDBDataStore.open(relativePath = folder, dataModelsById = dataModelsForTests)
        var store = open()
        try {
            val (request, committed) = createAtomicReceiptForReopen(store)
            val (journal, journalCommit) = createTransactionReceiptForReopen(store)
            store.close()
            store = open()
            assertEquals(committed, store.executeAtomic(request))
            assertEquals(journalCommit, store.executeTransaction(journal))
        } finally { store.close(); deleteFolder(folder) }
    }
    @Test
    fun transactionContractWithoutHistory() = runTest {
        val folder = createTestDBFolder("transaction-current")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = dataModelsForTests)
        try { runTransactionDataStoreTests(store) } finally { store.close(); deleteFolder(folder) }
    }

}

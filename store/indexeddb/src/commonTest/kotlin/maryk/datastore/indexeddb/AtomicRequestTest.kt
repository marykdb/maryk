package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runTransactionDataStoreTests
import maryk.datastore.test.runAtomicDataStoreTests
import maryk.datastore.test.createAtomicReceiptForReopen
import maryk.datastore.test.createTransactionReceiptForReopen
import maryk.datastore.shared.executeTransaction
import maryk.datastore.shared.executeAtomic
import kotlin.test.assertEquals
import maryk.datastore.test.testTransactionCompetingStores
import maryk.datastore.test.testAtomicCompetingStores
import maryk.datastore.shared.supportsAtomicRequests
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.random.Random

class AtomicRequestTest {
    @Test
    fun sharedAtomicContract() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open(databaseName = "maryk-atomic-${Random.nextInt()}", dataModelsById = dataModelsForTests, keepAllVersions = true, keepUpdateHistoryIndex = true)
        try { assertTrue(store.supportsAtomicRequests); runAtomicDataStoreTests(store); runTransactionDataStoreTests(store) } finally { store.close() }
    }
    @Test
    fun competingBrowserContexts() = runTest {
        installIndexedDbForTests()
        val database = "maryk-atomic-race-${Random.nextInt()}"
        val first = IndexedDbDataStore.open(database, dataModelsForTests)
        try {
            val second = IndexedDbDataStore.open(database, dataModelsForTests)
            try { testAtomicCompetingStores(first, second); testTransactionCompetingStores(first, second) } finally { second.close() }
        } finally { first.close() }
    }
    @Test
    fun receiptSurvivesReopen() = runTest {
        installIndexedDbForTests()
        val database = "maryk-atomic-receipt-${Random.nextInt()}"
        var store = IndexedDbDataStore.open(database, dataModelsForTests)
        try {
            val (request, committed) = createAtomicReceiptForReopen(store)
            val (journal, journalCommit) = createTransactionReceiptForReopen(store)
            store.close()
            store = IndexedDbDataStore.open(database, dataModelsForTests)
            assertEquals(committed, store.executeAtomic(request))
            assertEquals(journalCommit, store.executeTransaction(journal))
        } finally { store.close() }
    }
    @Test
    fun transactionContractWithoutHistory() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open(databaseName = "maryk-transaction-current-${Random.nextInt()}", dataModelsById = dataModelsForTests)
        try { runTransactionDataStoreTests(store) } finally { store.close() }
    }

}

package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.cancelAndJoin
import maryk.core.clock.HLC
import maryk.core.models.key
import maryk.core.query.requests.get
import maryk.core.query.requests.change
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.shared.beginTransaction
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.requests.add
import maryk.core.query.pairs.with
import maryk.core.query.responses.AtomicStatus
import maryk.test.models.SimpleMarykModel
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runTransactionDataStoreTests
import maryk.datastore.test.runAtomicDataStoreTests
import maryk.datastore.test.createAtomicReceiptForReopen
import maryk.datastore.test.createTransactionReceiptForReopen
import maryk.datastore.shared.executeTransaction
import maryk.datastore.shared.TransactionAbort
import maryk.core.query.responses.TransactionResponse
import maryk.datastore.shared.rejectAtomicLimit
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
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "atomic", Random.nextInt().toString()), dataModelsById = dataModelsForTests, keepAllVersions = true, keepUpdateHistoryIndex = true)
        try { assertTrue(store.supportsAtomicRequests); runAtomicDataStoreTests(store); runTransactionDataStoreTests(store) } finally { store.close() }
    }
    @Test
    fun competingStoreInstances() = runTest {
        val directory = listOf("maryk", "atomic-race", Random.nextInt().toString())
        val first = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory, dataModelsById = dataModelsForTests)
        try {
            val second = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory, dataModelsById = dataModelsForTests)
            try { testAtomicCompetingStores(first, second); testTransactionCompetingStores(first, second) } finally { second.close() }
        } finally { first.close() }
    }
    @Test
    fun receiptSurvivesReopen() = runTest {
        val directory = listOf("maryk", "atomic-receipt", Random.nextInt().toString())
        suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory, dataModelsById = dataModelsForTests)
        var store = open()
        try {
            val (request, committed) = createAtomicReceiptForReopen(store)
            val (journal, journalCommit) = createTransactionReceiptForReopen(store)
            store.close()
            store = open()
            assertEquals(committed, store.executeAtomic(request))
            assertEquals(journalCommit, store.executeTransaction(journal))
        } finally { store.close() }
    }
    @Test
    fun nativeValueLimitReportsRejectionAndRollsBackEarlierMember() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "atomic-value-limit", Random.nextInt().toString()), dataModelsById = dataModelsForTests)
        try {
            val request = AtomicRequest("native-value-limit",
                SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-first" }),
                SimpleMarykModel.add(SimpleMarykModel.create { value with ("ha" + "x".repeat(100_000)) }))
            val result = store.executeAtomic(request)
            assertEquals(AtomicStatus.Rejected, result.status)
            assertEquals(AtomicFailureCode.LimitExceeded, result.failure?.code)
            assertEquals("storageValueBytes", result.failure?.limit)
            assertEquals(100_000uL, result.failure?.maximum)
            assertTrue(store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        } finally { store.close() }
    }

    @Test
    fun limitShapedNotificationFailureDoesNotClaimRollback() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "atomic-postcommit-limit", Random.nextInt().toString()), dataModelsById = dataModelsForTests)
        try {
            val request = AtomicRequest("postcommit-limit", SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-postcommit" }))
            store.beforeUpdateEmission.value = { rejectAtomicLimit("workBytes", 0uL, 1uL) }
            assertEquals(AtomicStatus.OutcomeUnknown, store.executeAtomic(request).status)
            store.beforeUpdateEmission.value = null
            assertEquals(AtomicStatus.Committed, store.executeAtomic(request).status)
        } finally { store.beforeUpdateEmission.value = null; store.close() }
    }

    @Test
    fun retryRecoversCommitAfterNotificationFailure() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "atomic-unknown", Random.nextInt().toString()), dataModelsById = dataModelsForTests)
        try {
            val request = AtomicRequest("unknown", SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-unknown" }))
            store.beforeUpdateEmission.value = { error("Injected failure after native commit") }
            assertEquals(AtomicStatus.OutcomeUnknown, store.executeAtomic(request).status)
            store.beforeUpdateEmission.value = null
            assertEquals(AtomicStatus.Committed, store.executeAtomic(request).status)
        } finally { store.beforeUpdateEmission.value = null; store.close() }
    }
    @Test
    fun transactionStartsAboveVersionsWrittenByAnotherLiveInstance() = runTest {
        val directory = listOf("maryk", "transaction-clock", Random.nextInt().toString())
        val first = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory, dataModelsById = dataModelsForTests)
        val second = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory, dataModelsById = dataModelsForTests)
        try {
            val values = SimpleMarykModel.create { value with "haha-future" }
            val key = SimpleMarykModel.key(values)
            val future = HLC(HLC().toPhysicalUnixTime() + 60_000uL, 0u).timestamp
            first.processUpdate(UpdateResponse(SimpleMarykModel, AdditionUpdate(key, future, future, 0, false, values)))
            val tx = second.beginTransaction()
            assertTrue(tx.stagingVersion > future)
            tx.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-first"))))
            val staged = tx.execute(SimpleMarykModel.get(key)).response.values.single()
            tx.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-final"), lastVersion = staged.lastVersion)))
            assertEquals(TransactionStatus.Committed, tx.commit().status)
            assertEquals("haha-final", first.execute(SimpleMarykModel.get(key)).values.single().values[SimpleMarykModel.ref { value }])
        } finally { second.close(); first.close() }
    }

    @Test
    fun postCommitTransactionAbortCannotClaimRollback() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "transaction-postcommit", Random.nextInt().toString()), dataModelsById = dataModelsForTests)
        try {
            val tx = store.beginTransaction()
            tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-published" }))
            store.beforeUpdateEmission.value = { throw TransactionAbort(TransactionResponse(TransactionStatus.Aborted)) }
            assertEquals(TransactionStatus.OutcomeUnknown, tx.commit().status)
            store.beforeUpdateEmission.value = null
            assertEquals(TransactionStatus.Committed, tx.resolveCommit().status)
        } finally { store.beforeUpdateEmission.value = null; store.close() }
    }

    @Test
    fun notificationFailureTerminatesListenersInsteadOfLeavingThemStale() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "transaction-notifications", Random.nextInt().toString()), dataModelsById = dataModelsForTests)
        val ready = Channel<Unit>(1)
        val failures = Channel<Throwable>(1)
        val listener = launch {
            store.executeFlow(SimpleMarykModel.scan(allowTableScan = true))
                .catch { failures.send(it) }.collect { ready.trySend(Unit) }
        }
        try {
            ready.receive()
            val tx = store.beginTransaction()
            tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-notification" }))
            store.beforeUpdateEmission.value = { error("Injected notification failure") }
            assertEquals(TransactionStatus.OutcomeUnknown, tx.commit().status)
            assertTrue(failures.receive().message.orEmpty().contains("notifications"))
            store.beforeUpdateEmission.value = null
            assertEquals(TransactionStatus.Committed, tx.resolveCommit().status)
        } finally { listener.cancelAndJoin(); store.beforeUpdateEmission.value = null; store.close() }
    }

    @Test
    fun transactionContractWithoutHistory() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "transaction-current", Random.nextInt().toString()), dataModelsById = dataModelsForTests)
        try { runTransactionDataStoreTests(store) } finally { store.close() }
    }

}

package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.requests.add
import maryk.core.query.pairs.with
import maryk.core.query.responses.AtomicStatus
import maryk.test.models.SimpleMarykModel
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runAtomicDataStoreTests
import maryk.datastore.test.createAtomicReceiptForReopen
import maryk.datastore.shared.rejectAtomicLimit
import maryk.datastore.shared.executeAtomic
import kotlin.test.assertEquals
import maryk.datastore.test.testAtomicCompetingStores
import maryk.datastore.shared.supportsAtomicRequests
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.random.Random

class AtomicRequestTest {
    @Test
    fun sharedAtomicContract() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "atomic", Random.nextInt().toString()), dataModelsById = dataModelsForTests, keepAllVersions = true, keepUpdateHistoryIndex = true)
        try { assertTrue(store.supportsAtomicRequests); runAtomicDataStoreTests(store) } finally { store.close() }
    }
    @Test
    fun competingStoreInstances() = runTest {
        val directory = listOf("maryk", "atomic-race", Random.nextInt().toString())
        val first = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory, dataModelsById = dataModelsForTests)
        try {
            val second = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory, dataModelsById = dataModelsForTests)
            try { testAtomicCompetingStores(first, second) } finally { second.close() }
        } finally { first.close() }
    }
    @Test
    fun receiptSurvivesReopen() = runTest {
        val directory = listOf("maryk", "atomic-receipt", Random.nextInt().toString())
        suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory, dataModelsById = dataModelsForTests)
        var store = open()
        try {
            val (request, committed) = createAtomicReceiptForReopen(store)
            store.close()
            store = open()
            assertEquals(committed, store.executeAtomic(request))
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
}

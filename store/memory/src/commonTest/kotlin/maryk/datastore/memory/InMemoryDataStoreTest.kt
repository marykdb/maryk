package maryk.datastore.memory

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.exceptions.StorageException
import maryk.core.models.key
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.getUpdates
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class InMemoryDataStoreTest {
    @Test
    fun testDataStore() = runTest(timeout = 3.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        try {
            runDataStoreTests(dataStore)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest(timeout = 3.minutes) {
        val dataStore = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = dataModelsForTests)
        try {
            runDataStoreTests(dataStore)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun testDataStoreWithUpdateHistoryIndex() = runTest(timeout = 3.minutes) {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = dataModelsForTests
        )
        try {
            runDataStoreTests(dataStore, "executeSimpleScanUpdatesRequestWithUpdateHistoryIndex")
            runDataStoreTests(dataStore, "executeHistoryStyleScanUpdatesRequestFallsBackWithoutUpdateHistoryIndex")
            runDataStoreTests(dataStore, "executeScanUpdateHistoryReturnsVersionOrderedEntries")
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun closeAllListenersBeforeRequestsDoesNotHang() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        try {
            withContext(Dispatchers.Default) {
                withTimeout(1.seconds) {
                    dataStore.closeAllListeners()
                }
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun closeAllListenersAfterCloseDoesNotHang() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        dataStore.close()

        withContext(Dispatchers.Default) {
            withTimeout(1.seconds) {
                dataStore.closeAllListeners()
            }
        }
    }

    @Test
    fun executeAfterCloseFailsImmediately() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        dataStore.close()

        assertFailsWith<StorageException> {
            dataStore.execute(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16))))
        }
    }

    @Test
    fun cancellingFlowAfterCloseDoesNotFailRemovingListener() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        val key = SimpleMarykModel.key(ByteArray(16))
        dataStore.execute(SimpleMarykModel.add(key to SimpleMarykModel.create { value with "flow close" }))

        val flow = dataStore.executeFlow(SimpleMarykModel.getUpdates(key))
        val collector = launch {
            flow.collect()
        }

        try {
            dataStore.close()
            collector.cancelAndJoin()
        } finally {
            dataStore.close()
        }
    }
}

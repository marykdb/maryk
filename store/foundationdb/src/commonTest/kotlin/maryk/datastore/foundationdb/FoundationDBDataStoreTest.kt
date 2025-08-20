package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import kotlin.test.Ignore
import kotlin.test.Test

class FoundationDBDataStoreTest {
    // NOTE: Running this test requires a local FoundationDB server. If unavailable, the test is ignored.

    @Test
    fun testDataStore() = runTest {
        val dataStore = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )

        runDataStoreTests(dataStore, runOnlyTest = "executeAddAndSimpleGetRequest")

        dataStore.close()
    }

    @Ignore("Requires local FoundationDB server; enable when FDB is available")
    @Test
    fun testDataStoreWithKeepAllVersions() = runTest {
        val dataStore = FoundationDBDataStore.open(
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }
}

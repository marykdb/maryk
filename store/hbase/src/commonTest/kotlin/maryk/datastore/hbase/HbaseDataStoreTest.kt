package maryk.datastore.hbase

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import org.apache.hadoop.hbase.client.ConnectionFactory
import org.apache.hadoop.hbase.testing.TestingHBaseCluster
import org.apache.hadoop.hbase.testing.TestingHBaseClusterOption
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class HbaseDataStoreTest {
    private val cluster: TestingHBaseCluster = TestingHBaseCluster.create(
        TestingHBaseClusterOption.builder().build()
    )

    init {
        cluster.start()
    }

    private val connection = ConnectionFactory.createAsyncConnection(cluster.conf).get()

    @Test
    fun testDataStore() = runTest(timeout = 60.seconds) {
        val dataStore = HbaseDataStore(
            connection = connection,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest(timeout = 60.seconds) {
        val dataStore = HbaseDataStore(
            connection = connection,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }
}

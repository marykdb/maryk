@file:OptIn(ExperimentalCoroutinesApi::class)

package maryk.datastore.hbase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import org.apache.hadoop.hbase.client.ConnectionFactory
import org.apache.hadoop.hbase.testing.TestingHBaseCluster
import org.apache.hadoop.hbase.testing.TestingHBaseClusterOption
import kotlin.test.Test

class HbaseDataStoreTest {
    private val cluster: TestingHBaseCluster = TestingHBaseCluster.create(
        TestingHBaseClusterOption.builder().build()
    )

    init {
        cluster.start()
    }

    private val connection = ConnectionFactory.createConnection(cluster.conf);

    @Test
    fun testDataStore() = runTest {
        val dataStore = HbaseDataStore(
            connection = connection,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest {
        val dataStore = HbaseDataStore(
            connection = connection,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }
}

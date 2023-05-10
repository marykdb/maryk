package maryk.datastore.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.test.runSuspendingTest
import org.junit.Test
import java.net.InetSocketAddress

class CassandraDataStoreTest {
    private var sessionBuilder = CqlSession.builder()
        .addContactPoint(InetSocketAddress("127.0.0.1", 9142))
        .withLocalDatacenter("datacenter1")

    init {
        CassandraTestServer
    }

    @Test
    fun testDataStore() = runSuspendingTest {
        val dataStore = CassandraDataStore(
            sessionBuilder = sessionBuilder,
            keyspace = "maryk_test",
            dataModelsById = dataModelsForTests
        )

        runDataStoreTests(dataStore)
        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runSuspendingTest {
        val dataStore = CassandraDataStore(
            sessionBuilder = sessionBuilder,
            keyspace = "maryk_test_keep_all_versions",
            keepAllVersions = true,
            dataModelsById = dataModelsForTests,
        )

        runDataStoreTests(dataStore)
        dataStore.close()
    }
}

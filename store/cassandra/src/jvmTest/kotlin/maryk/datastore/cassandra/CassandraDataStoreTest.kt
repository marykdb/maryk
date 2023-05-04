package maryk.datastore.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.test.runSuspendingTest
import org.apache.cassandra.service.CassandraDaemon
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress

class CassandraDataStoreTest {
    private var session: CqlSession? = null

    @Before
    fun startServer() = runSuspendingTest {
        var cassandraConfigFilePath: String = File("cassandra.yaml").absolutePath
        cassandraConfigFilePath =
            (if (cassandraConfigFilePath.startsWith("/")) "file://" else "file:/") + cassandraConfigFilePath
        System.setProperty("cassandra.config", cassandraConfigFilePath)
        System.setProperty("cassandra-foreground", "true")
        System.setProperty("cassandra.native.epoll.enabled", "false") // JNA doesnt cope with relocated netty
        System.setProperty("cassandra.unsafesystem", "true") // disable fsync for a massive speedup on old platters

        val cassandraDaemon = CassandraDaemon()
        cassandraDaemon.activate()

        session = CqlSession.builder()
            .addContactPoint(InetSocketAddress("127.0.0.1", 9142))
            .withLocalDatacenter("datacenter1")
            .build()
    }

    @Test
    fun testDataStore() = runSuspendingTest {
        val dataStore = CassandraDataStore(
            session = session!!,
            dataModelsById = dataModelsForTests
        )

        try {
            runDataStoreTests(dataStore)
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runSuspendingTest {
        val dataStore = CassandraDataStore(
            session = session!!,
            keepAllVersions = true,
            dataModelsById = dataModelsForTests,
        )

        try {
            runDataStoreTests(dataStore)
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            dataStore.close()
        }
    }
}

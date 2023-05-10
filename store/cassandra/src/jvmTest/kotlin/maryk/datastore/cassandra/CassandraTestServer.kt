package maryk.datastore.cassandra

import kotlinx.coroutines.runBlocking
import org.apache.cassandra.service.CassandraDaemon
import java.io.File

object CassandraTestServer {
    private var isStarted: Boolean = false
    private var cassandraDaemon: CassandraDaemon? = null

    init {
        if (!isStarted) {
            runBlocking {
                var cassandraConfigFilePath: String = File("cassandra.yaml").absolutePath
                cassandraConfigFilePath =
                    (if (cassandraConfigFilePath.startsWith("/")) "file://" else "file:/") + cassandraConfigFilePath
                System.setProperty("cassandra.config", cassandraConfigFilePath)
                System.setProperty("cassandra-foreground", "true")
                System.setProperty("cassandra.native.epoll.enabled", "false") // JNA doesnt cope with relocated netty
                System.setProperty("cassandra.unsafesystem", "true") // disable fsync for a massive speedup on old platters

                cassandraDaemon = CassandraDaemon()
                cassandraDaemon!!.activate()
            }
            isStarted = true
        }
    }
}

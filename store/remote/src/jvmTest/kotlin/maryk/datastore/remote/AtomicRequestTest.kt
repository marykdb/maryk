package maryk.datastore.remote

import java.net.ServerSocket
import maryk.core.query.pairs.with
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.Requests
import maryk.core.query.requests.get
import maryk.core.models.key
import maryk.datastore.shared.transaction
import kotlin.test.assertFailsWith
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicStatus
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.executeAtomic
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertNull
import maryk.datastore.shared.supportsAtomicRequests
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runTransactionDataStoreTests
import maryk.datastore.test.runAtomicDataStoreTests
import maryk.test.models.SimpleMarykModel
import maryk.test.models.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtomicRequestTest {
    @Test
    fun sharedAtomicContractThroughRemote() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = dataModelsForTests, keepAllVersions = true, keepUpdateHistoryIndex = true)
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        try {
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            try {
                assertTrue(remote.supportsAtomicRequests)
                runAtomicDataStoreTests(remote); runTransactionDataStoreTests(remote)
            } finally { remote.close() }
        } finally { server.stop(0, 1000); store.close() }
    }

    @Test
    fun unsupportedBackendHasNoAtomicLimitReport() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        val legacy = object : IsDataStore by store { }
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(legacy).start("127.0.0.1", port, wait = false)
        try {
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            try {
                assertEquals(false, remote.supportsAtomicRequests)
                assertNull(remote.atomicCapabilities)
            } finally { remote.close() }
        } finally { server.stop(0, 1000); store.close() }
    }

    @Test
    fun authorizesEveryModelBeforeWriting() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(store).start("127.0.0.1", port, wait = false,
            config = RemoteStoreServerConfig(authorizer = RemoteStoreAuthorizer { it.modelName != Person.Meta.name }))
        try {
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            try {
                val request = AtomicRequest("authorization", SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-allowed" }),
                    Person.add(Person.create { firstName with "Denied"; surname with "Person" }))
                assertEquals(AtomicStatus.Rejected, remote.executeAtomic(request).status)
                assertTrue(store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
                assertTrue(store.execute(Person.scan(allowTableScan = true)).values.isEmpty())
            } finally { remote.close() }
        } finally { server.stop(0, 1000); store.close() }
    }
    @Test
    fun remoteBatchJoinsTheAmbientTransaction() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        try {
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            try {
                val values = SimpleMarykModel.create { value with "haha-batch-private" }
                val key = SimpleMarykModel.key(values)
                assertFailsWith<IllegalStateException> {
                    remote.transaction {
                        val responses = remote.execute(Requests(SimpleMarykModel.add(key to values), SimpleMarykModel.get(key)))
                        assertEquals(2, responses.size)
                        assertTrue(store.execute(SimpleMarykModel.get(key)).values.isEmpty())
                        error("Discard the entire batch")
                    }
                }
                assertTrue(store.execute(SimpleMarykModel.get(key)).values.isEmpty())
            } finally { remote.close() }
        } finally { server.stop(0, 1000); store.close() }
    }

    @Test
    fun transactionContractWithoutHistoryThroughRemote() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        try {
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            try { runTransactionDataStoreTests(remote) } finally { remote.close() }
        } finally { server.stop(0, 1000); store.close() }
    }

}

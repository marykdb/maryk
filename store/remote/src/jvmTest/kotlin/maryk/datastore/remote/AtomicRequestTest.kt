package maryk.datastore.remote

import java.net.ServerSocket
import maryk.core.query.pairs.with
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicStatus
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.executeAtomic
import maryk.datastore.shared.supportsAtomicRequests
import maryk.datastore.test.dataModelsForTests
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
                runAtomicDataStoreTests(remote)
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
}

package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import maryk.core.exceptions.RequestException
import maryk.core.query.requests.scan
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.atomicRestoreModels
import maryk.datastore.test.checkAtomicRestore
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteAtomicRestoreTest {
    @Test
    fun restoresAtomicTransfersAcrossModelsOverHttp() = runBoundedIntegrationTest {
        withRemote { source, remote, _ -> checkAtomicRestore(source, remote, receiverFirst = true, batchSize = 1u) }
    }

    @Test
    fun authorizesEveryModelBeforeReplayingTheGroup() = runBoundedIntegrationTest {
        val config = RemoteStoreServerConfig(authorizer = RemoteStoreAuthorizer { request ->
            request.operation != RemoteStoreOperation.ProcessUpdate || request.modelName != SimpleMarykModel.Meta.name
        })
        withRemote(config) { source, remote, target ->
            val error = assertFailsWith<IllegalStateException> { checkAtomicRestore(source, remote, receiverFirst = true, batchSize = 1u) }
            assertTrue(error.message.orEmpty().contains("403"))
            assertEquals("transfer@test.com", target.execute(UniqueModel.scan(allowTableScan = true)).values.single().values { email })
            assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        }
    }

    @Test
    fun refusesServersWithoutVersionGroupCapabilityBeforeReplay() = runBoundedIntegrationTest {
        withRemote(advertise = false) { source, remote, target ->
            assertFailsWith<RequestException> { checkAtomicRestore(source, remote, receiverFirst = true, batchSize = 1u) }
            assertTrue(target.execute(UniqueModel.scan(allowTableScan = true)).values.isEmpty())
        }
    }

    private suspend fun withRemote(
        config: RemoteStoreServerConfig = RemoteStoreServerConfig(),
        advertise: Boolean = true,
        check: suspend (IsDataStore, RemoteDataStore, IsDataStore) -> Unit,
    ) {
        val source = InMemoryDataStore.open(dataModelsById = atomicRestoreModels, keepAllVersions = true)
        val target = InMemoryDataStore.open(dataModelsById = atomicRestoreModels, keepAllVersions = true)
        val served = if (advertise) target else object : IsDataStore by target {}
        val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) { remoteStoreModule(served, config) }.start(wait = false)
        val port = server.engine.resolvedConnectors().single().port
        val client = HttpClient(CIO)
        try {
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port", httpClient = client))
            try { check(source, remote, target) } finally { remote.close() }
        } finally {
            client.close()
            server.stop(500, 500)
            source.close()
            target.close()
        }
    }
}

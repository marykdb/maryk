package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.requests.scan
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.test.StorageUpgradeFixture
import maryk.datastore.test.StorageUpgradeRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteFloatingStorageTest {
    @Test
    fun packedFloatingValuesRoundTripAndLegacyClientsAreRejectedBeforeDecoding() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to StorageUpgradeRecord), keepAllVersions = true)
        val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) { remoteStoreModule(store) }.start(wait = false)
        val port = server.engine.resolvedConnectors().single().port
        val client = HttpClient(CIO)
        val url = "http://127.0.0.1:$port"
        try {
            val oldClient = client.post(url + RemoteStoreProtocol.executePath) { setBody(byteArrayOf(0)) }
            assertEquals(HttpStatusCode.BadRequest, oldClient.status)
            assertTrue(store.execute(StorageUpgradeRecord.scan(allowTableScan = true)).values.isEmpty())
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = url, httpClient = client))
            try {
                val fixture = StorageUpgradeFixture()
                fixture.addTo(remote)
                fixture.verify(remote)
            } finally { remote.close() }
        } finally {
            client.close()
            server.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun newClientRejectsOldServerForPackedFloatingModels() = runBoundedIntegrationTest {
        val info = RemoteStoreInfo(
            definitions = RemoteDataStore.collectDefinitions(listOf(StorageUpgradeRecord)),
            modelIds = listOf(RemoteStoreModelId(1u, StorageUpgradeRecord.Meta.name)),
            keepAllVersions = true,
            supportsFuzzyQualifierFiltering = true,
            supportsSubReferenceFiltering = true,
        )
        val bytes = RemoteStoreCodec.encode(RemoteStoreInfo.Serializer, info, DefinitionsConversionContext())
        val client = HttpClient(MockEngine { respond(bytes, headers = headersOf(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)) })
        try {
            val error = assertFailsWith<IllegalArgumentException> {
                RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://localhost", httpClient = client))
            }
            assertTrue(error.message.orEmpty().contains("upgrade the client and server together"))
        } finally { client.close() }
    }
}

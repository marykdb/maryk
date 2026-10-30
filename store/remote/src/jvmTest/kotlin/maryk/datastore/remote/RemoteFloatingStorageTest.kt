package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import kotlinx.datetime.LocalDateTime
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.set
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.test.StorageUpgradeFixture
import maryk.datastore.test.StorageUpgradeRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object NanosRemoteRecord : RootDataModel<NanosRemoteRecord>() {
    val stamps by list(1u, valueDefinition = DateTimeDefinition(precision = NANOS))
    val distinctStamps by set(2u, valueDefinition = DateTimeDefinition(precision = NANOS))
    val stamp by dateTime(3u, precision = NANOS)
}

class RemoteFloatingStorageTest {
    @Test
    fun nanosValuesRoundTripAndLegacyClientsAreRejectedBeforeDecoding() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to NanosRemoteRecord), keepAllVersions = true)
        val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) { remoteStoreModule(store) }.start(wait = false)
        val port = server.engine.resolvedConnectors().single().port
        val client = HttpClient(CIO)
        val url = "http://127.0.0.1:$port"
        try {
            val oldClient = client.post(url + RemoteStoreProtocol.executePath) { setBody(byteArrayOf(0)) }
            assertEquals(HttpStatusCode.BadRequest, oldClient.status)
            assertTrue(oldClient.bodyAsText().contains("upgrade the client and server together"))
            assertTrue(store.execute(NanosRemoteRecord.scan(allowTableScan = true)).values.isEmpty())
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = url, httpClient = client))
            try {
                val stamps = listOf(LocalDateTime(1970, 1, 1, 0, 0, 8), LocalDateTime(1970, 1, 1, 0, 0, 16, 42))
                val values = NanosRemoteRecord.create {
                    NanosRemoteRecord.stamps with stamps
                    distinctStamps with stamps.toSet()
                    stamp with LocalDateTime(1969, 12, 31, 23, 59, 59, 999_999_999)
                }
                val added = assertIs<AddSuccess<NanosRemoteRecord>>(remote.execute(NanosRemoteRecord.add(values)).statuses.single())
                assertEquals(values, remote.execute(NanosRemoteRecord.get(added.key)).values.single().values)
            } finally { remote.close() }
        } finally {
            client.close()
            server.stop(500, 500)
            store.close()
        }
    }

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
            assertTrue(oldClient.bodyAsText().contains("upgrade the client and server together"))
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
        assertNewClientRejectsOldServer(StorageUpgradeRecord)
    }

    @Test
    fun newClientRejectsOldServerForNanosOnlyModels() = runBoundedIntegrationTest {
        assertNewClientRejectsOldServer(NanosRemoteRecord)
    }

    private suspend fun assertNewClientRejectsOldServer(dataModel: IsRootDataModel) {
        val info = RemoteStoreInfo(
            definitions = RemoteDataStore.collectDefinitions(listOf(dataModel)),
            modelIds = listOf(RemoteStoreModelId(1u, dataModel.Meta.name)),
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

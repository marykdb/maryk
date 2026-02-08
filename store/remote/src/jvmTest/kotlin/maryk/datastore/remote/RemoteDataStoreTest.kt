package maryk.datastore.remote

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.IsChange
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.memory.InMemoryDataStore
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel

class RemoteDataStoreTest {
    @Test
    fun remoteExecuteAndFlow() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val info = RemoteStoreInfo(
            definitions = RemoteDataStore.collectDefinitions(store.dataModelsById.values),
            modelIds = store.dataModelsById.map { (id, model) ->
                RemoteStoreModelId(id = id, name = model.Meta.name)
            },
            keepAllVersions = store.keepAllVersions,
            supportsFuzzyQualifierFiltering = store.supportsFuzzyQualifierFiltering,
            supportsSubReferenceFiltering = store.supportsSubReferenceFiltering,
        )
        val infoBytes = RemoteStoreCodec.encode(RemoteStoreInfo.Serializer, info, DefinitionsConversionContext())
        RemoteStoreCodec.decode(RemoteStoreInfo.Serializer, infoBytes, DefinitionsConversionContext())
        val port = ServerSocket(0).use { it.localPort }
        val engine = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)

        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port")
        )

        try {
            val values = SimpleMarykModel.create {
                value with "haha"
            }
            val addResponse: AddResponse<SimpleMarykModel> = remote.execute(SimpleMarykModel.add(values))
            val status = addResponse.statuses.firstOrNull()
            assertNotNull(status)
            val addSuccess = status as? AddSuccess<SimpleMarykModel>
            assertNotNull(addSuccess)
            assertTrue(addSuccess.key.bytes.isNotEmpty())

            val getResponse: ValuesResponse<SimpleMarykModel> =
                remote.execute(SimpleMarykModel.get(addSuccess.key))
            val fetched = getResponse.values.firstOrNull()
            assertNotNull(fetched)
            assertEquals("haha", fetched.values { value })

            val flowRequest = SimpleMarykModel.get(addSuccess.key)
            val initialUpdate = remote.executeFlow(flowRequest).first()
            assertTrue(initialUpdate is InitialValuesUpdate<*>)
        } finally {
            remote.close()
            engine.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun executeFailsOnTrailingBytes() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            malformedExecuteModule()
        }.start(wait = false)

        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port")
        )

        try {
            val values = SimpleMarykModel.create {
                value with "hello"
            }
            val exception = assertFailsWith<IllegalStateException> {
                remote.execute(SimpleMarykModel.add(values))
            }
            assertTrue(exception.message?.contains("trailing bytes") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun connectRejectsNonHttpUrl() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "https://127.0.0.1:8210"))
        }
        assertTrue(exception.message?.contains("only supports http URLs") == true)
    }

    @Test
    fun connectRejectsBaseUrlWithQuery() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:8210?x=1"))
        }
        assertTrue(exception.message?.contains("query parameters") == true)
    }

    @Test
    fun connectRejectsBaseUrlWithFragment() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:8210#anchor"))
        }
        assertTrue(exception.message?.contains("fragment") == true)
    }

    @Test
    fun connectRejectsBaseUrlWithUserInfo() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://user:pass@127.0.0.1:8210"))
        }
        assertTrue(exception.message?.contains("user info") == true)
    }

    @Test
    fun connectRejectsBaseUrlWithInvalidPort() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:70000"))
        }
        assertTrue(exception.message?.contains("70000") == true)
    }

    @Test
    fun connectRejectsMalformedBaseUrl() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:abc"))
        }
        assertTrue(exception.message?.contains("invalid") == true)
    }

    @Test
    fun connectRejectsBlankBaseUrl() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "   "))
        }
        val message = exception.message.orEmpty()
        assertTrue(message.contains("blank") || message.contains("whitespace"))
    }

    @Test
    fun connectRejectsBaseUrlWithLeadingWhitespace() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = " http://127.0.0.1:8210"))
        }
        assertTrue(exception.message?.contains("leading or trailing whitespace") == true)
    }

    @Test
    fun connectRejectsBlankSshHost() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = ""),
                )
            )
        }
        assertTrue(exception.message?.contains("SSH host cannot be blank") == true)
    }

    @Test
    fun connectRejectsOutOfRangeSshPort() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", port = 70000),
                )
            )
        }
        assertTrue(exception.message?.contains("SSH port must be between") == true)
    }

    @Test
    fun connectRejectsOutOfRangeSshLocalPort() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", localPort = 70000),
                )
            )
        }
        assertTrue(exception.message?.contains("local port") == true)
    }

    @Test
    fun connectRejectsOutOfRangeSshRemotePort() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", remotePort = 70000),
                )
            )
        }
        assertTrue(exception.message?.contains("remote port") == true)
    }

    @Test
    fun connectRejectsBlankSshUser() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", user = " "),
                )
            )
        }
        assertTrue(exception.message?.contains("SSH user cannot be blank") == true)
    }

    @Test
    fun connectRejectsBlankSshRemoteHost() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", remoteHost = " "),
                )
            )
        }
        assertTrue(exception.message?.contains("remote host cannot be blank") == true)
    }

    @Test
    fun connectRejectsBlankSshIdentityFile() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", identityFile = " "),
                )
            )
        }
        assertTrue(exception.message?.contains("identity file cannot be blank") == true)
    }

    @Test
    fun connectRejectsBlankSshExtraArgs() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", extraArgs = listOf("-N", " ")),
                )
            )
        }
        assertTrue(exception.message?.contains("extra arguments") == true)
    }

    @Test
    fun connectRejectsDuplicateModelIds() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            duplicateInfoModule(
                modelIds = listOf(
                    RemoteStoreModelId(1u, SimpleMarykModel.Meta.name),
                    RemoteStoreModelId(1u, SimpleMarykModel.Meta.name),
                ),
            )
        }.start(wait = false)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            }
            assertTrue(exception.message?.contains("Duplicate model id") == true)
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun connectRejectsDuplicateModelNames() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            duplicateInfoModule(
                modelIds = listOf(
                    RemoteStoreModelId(1u, SimpleMarykModel.Meta.name),
                    RemoteStoreModelId(2u, SimpleMarykModel.Meta.name),
                ),
            )
        }.start(wait = false)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            }
            assertTrue(exception.message?.contains("Duplicate model name") == true)
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun connectRejectsUnexpectedInfoContentType() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            wrongInfoContentTypeModule()
        }.start(wait = false)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            }
            assertTrue(exception.message?.contains("unexpected Content-Type") == true)
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun connectRejectsEmptyInfoPayload() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            emptyInfoPayloadModule()
        }.start(wait = false)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            }
            assertTrue(exception.message?.contains("empty payload") == true)
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun connectIncludesErrorBodyPreview() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            infoServerErrorModule()
        }.start(wait = false)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            }
            assertTrue(exception.message?.contains("boom-info") == true)
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFailsOnNegativeLengthPrefix() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            negativeLengthExecuteModule()
        }.start(wait = false)

        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port")
        )

        try {
            val values = SimpleMarykModel.create {
                value with "hello"
            }
            val exception = assertFailsWith<IllegalStateException> {
                remote.execute(SimpleMarykModel.add(values))
            }
            assertTrue(exception.message?.contains("Invalid response length prefix") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFailsOnZeroLengthPrefix() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            zeroLengthExecuteModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val values = SimpleMarykModel.create {
                value with "hello"
            }
            val exception = assertFailsWith<IllegalStateException> {
                remote.execute(SimpleMarykModel.add(values))
            }
            assertTrue(exception.message?.contains("Invalid response length prefix: 0") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFailsOnUnexpectedContentType() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            wrongExecuteContentTypeModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val values = SimpleMarykModel.create {
                value with "hello"
            }
            val exception = assertFailsWith<IllegalStateException> {
                remote.execute(SimpleMarykModel.add(values))
            }
            assertTrue(exception.message?.contains("unexpected Content-Type") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFailsOnEmptyPayload() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            emptyExecutePayloadModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val values = SimpleMarykModel.create {
                value with "hello"
            }
            val exception = assertFailsWith<IllegalStateException> {
                remote.execute(SimpleMarykModel.add(values))
            }
            assertTrue(exception.message?.contains("empty payload") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFailsOnOversizedLengthPrefix() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            oversizedLengthExecuteModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val values = SimpleMarykModel.create {
                value with "hello"
            }
            val exception = assertFailsWith<IllegalStateException> {
                remote.execute(SimpleMarykModel.add(values))
            }
            assertTrue(exception.message?.contains("frame exceeds max size") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnNegativeLengthPrefix() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            negativeLengthFlowModule()
        }.start(wait = false)

        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port")
        )

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertTrue(exception.message?.contains("Invalid streamed response length prefix") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnUnexpectedContentType() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            wrongFlowContentTypeModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertTrue(exception.message?.contains("unexpected Content-Type") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnZeroLengthPrefix() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            zeroLengthFlowModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertTrue(exception.message?.contains("Invalid streamed response length prefix: 0") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnOversizedLengthPrefix() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            oversizedLengthFlowModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertTrue(exception.message?.contains("frame exceeds max size") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowEndsWithoutUpdates(): Unit = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            emptyFlowStreamModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            assertFailsWith<NoSuchElementException> {
                withTimeout(2_000) {
                    remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
                }
            }
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnEmptyUpdateFrame() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            emptyFlowUpdatesModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertTrue(exception.message?.contains("empty update frame") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnTruncatedLengthPrefix() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            truncatedLengthFlowModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertTrue(exception.message?.isNotBlank() == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnTruncatedPayload() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            truncatedPayloadFlowModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertTrue(exception.message?.isNotBlank() == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnDataModelMismatch() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            mismatchedFlowDataModelModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertTrue(exception.message?.contains("data model mismatch") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }
}

private fun Application.malformedExecuteModule() {
    val infoBytes = defaultInfoBytes()

    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.executePath) {
            call.receiveChannel().readRemaining().readByteArray()
            val values = SimpleMarykModel.create {
                value with "response"
            }
            val key = SimpleMarykModel.key(values)
            val response = AddResponse(
                dataModel = SimpleMarykModel,
                statuses = listOf(AddSuccess(key = key, version = 1uL, changes = emptyList<IsChange>())),
            )
            val context = RequestContext(
                definitionsContext = DefinitionsContext(
                    dataModels = mutableMapOf(SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel))
                ),
                dataModel = SimpleMarykModel,
            )
            val payload = RemoteStoreCodec.encode(AddResponse.Serializer, response, context)
            val framed = RemoteStoreCodec.lengthPrefix(payload.size) + payload + byteArrayOf(0x01)
            call.respondBytes(
                framed,
                ContentType.parse(RemoteStoreProtocol.contentType),
                HttpStatusCode.OK,
            )
        }
    }
}

private fun Application.negativeLengthExecuteModule() {
    val infoBytes = defaultInfoBytes()

    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.executePath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytes(
                byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
                ContentType.parse(RemoteStoreProtocol.contentType),
                HttpStatusCode.OK,
            )
        }
    }
}

private fun Application.negativeLengthFlowModule() {
    val infoBytes = defaultInfoBytes()

    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                writeFully(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
                flush()
            }
        }
    }
}

private fun Application.zeroLengthExecuteModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.executePath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytes(
                byteArrayOf(0x00, 0x00, 0x00, 0x00),
                ContentType.parse(RemoteStoreProtocol.contentType),
                HttpStatusCode.OK,
            )
        }
    }
}

private fun Application.oversizedLengthExecuteModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.executePath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytes(
                byteArrayOf(0x01, 0x00, 0x00, 0x01),
                ContentType.parse(RemoteStoreProtocol.contentType),
                HttpStatusCode.OK,
            )
        }
    }
}

private fun Application.zeroLengthFlowModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                writeFully(byteArrayOf(0x00, 0x00, 0x00, 0x00))
                flush()
            }
        }
    }
}

private fun Application.oversizedLengthFlowModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                writeFully(byteArrayOf(0x01, 0x00, 0x00, 0x01))
                flush()
            }
        }
    }
}

private fun Application.duplicateInfoModule(modelIds: List<RemoteStoreModelId>) {
    val info = RemoteStoreInfo(
        definitions = RemoteDataStore.collectDefinitions(listOf(SimpleMarykModel)),
        modelIds = modelIds,
        keepAllVersions = true,
        supportsFuzzyQualifierFiltering = false,
        supportsSubReferenceFiltering = false,
    )
    val infoBytes = RemoteStoreCodec.encode(RemoteStoreInfo.Serializer, info, DefinitionsConversionContext())
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
    }
}

private fun Application.wrongInfoContentTypeModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.Text.Plain, HttpStatusCode.OK)
        }
    }
}

private fun Application.emptyInfoPayloadModule() {
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(byteArrayOf(), ContentType.parse(RemoteStoreProtocol.contentType), HttpStatusCode.OK)
        }
    }
}

private fun Application.infoServerErrorModule() {
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes("boom-info".encodeToByteArray(), ContentType.Text.Plain, HttpStatusCode.InternalServerError)
        }
    }
}

private fun Application.wrongExecuteContentTypeModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.executePath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytes("not-proto".encodeToByteArray(), ContentType.Text.Plain, HttpStatusCode.OK)
        }
    }
}

private fun Application.emptyExecutePayloadModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.executePath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytes(byteArrayOf(), ContentType.parse(RemoteStoreProtocol.contentType), HttpStatusCode.OK)
        }
    }
}

private fun Application.wrongFlowContentTypeModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytes("oops".encodeToByteArray(), ContentType.Text.Plain, HttpStatusCode.OK)
        }
    }
}

private fun Application.emptyFlowUpdatesModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                val context = RequestContext(
                    definitionsContext = DefinitionsContext(
                        dataModels = mutableMapOf(SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel))
                    ),
                    dataModel = SimpleMarykModel,
                )
                val payload = RemoteStoreCodec.encode(
                    UpdatesResponse.Serializer,
                    UpdatesResponse(SimpleMarykModel, emptyList()),
                    context,
                )
                writeFully(RemoteStoreCodec.lengthPrefix(payload.size))
                writeFully(payload)
                flush()
            }
        }
    }
}

private fun Application.truncatedLengthFlowModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                writeFully(byteArrayOf(0x00, 0x00))
                flush()
            }
        }
    }
}

private fun Application.truncatedPayloadFlowModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                writeFully(byteArrayOf(0x00, 0x00, 0x00, 0x08))
                writeFully(byteArrayOf(0x01, 0x02))
                flush()
            }
        }
    }
}

private fun Application.emptyFlowStreamModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                flush()
            }
        }
    }
}

private fun Application.mismatchedFlowDataModelModule() {
    val info = RemoteStoreInfo(
        definitions = RemoteDataStore.collectDefinitions(listOf(SimpleMarykModel, TestMarykModel)),
        modelIds = listOf(
            RemoteStoreModelId(1u, SimpleMarykModel.Meta.name),
            RemoteStoreModelId(2u, TestMarykModel.Meta.name),
        ),
        keepAllVersions = true,
        supportsFuzzyQualifierFiltering = false,
        supportsSubReferenceFiltering = false,
    )
    val infoBytes = RemoteStoreCodec.encode(RemoteStoreInfo.Serializer, info, DefinitionsConversionContext())
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                val context = RequestContext(
                    definitionsContext = DefinitionsContext(
                        dataModels = mutableMapOf(
                            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel),
                            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
                        )
                    ),
                    dataModel = TestMarykModel,
                )
                val payload = RemoteStoreCodec.encode(
                    UpdatesResponse.Serializer,
                    UpdatesResponse(
                        TestMarykModel,
                        listOf(
                            OrderedKeysUpdate(
                                keys = listOf(TestMarykModel.key("AAACKwEAAg")),
                                version = 1uL,
                            )
                        ),
                    ),
                    context,
                )
                writeFully(RemoteStoreCodec.lengthPrefix(payload.size))
                writeFully(payload)
                flush()
            }
        }
    }
}

private fun defaultInfoBytes(): ByteArray {
    val info = RemoteStoreInfo(
        definitions = RemoteDataStore.collectDefinitions(listOf(SimpleMarykModel)),
        modelIds = listOf(RemoteStoreModelId(1u, SimpleMarykModel.Meta.name)),
        keepAllVersions = true,
        supportsFuzzyQualifierFiltering = false,
        supportsSubReferenceFiltering = false,
    )
    return RemoteStoreCodec.encode(RemoteStoreInfo.Serializer, info, DefinitionsConversionContext())
}

package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.requests.Requests
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.memory.InMemoryDataStore
import maryk.test.models.SimpleMarykModel

class RemoteStoreServerTest {
    @Test
    fun infoAllowsArbitraryAcceptHeader() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.get("$baseUrl${RemoteStoreProtocol.infoPath}") {
                header(HttpHeaders.Accept, "application/json")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun infoAcceptsTypeWildcard() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.get("$baseUrl${RemoteStoreProtocol.infoPath}") {
                header(HttpHeaders.Accept, "application/*")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeRejectsMissingContentType() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }
    }

    @Test
    fun executeRejectsWrongContentType() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }
    }

    @Test
    fun executeAllowsArbitraryAcceptHeader() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "application/json")
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeRejectsEmptyPayload() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(byteArrayOf())
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun executeRejectsOversizedPayload() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(oversizedPayload())
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }
    }

    @Test
    fun executeRejectsMalformedPayload() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(byteArrayOf(1, 2, 3))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun executeRejectsEmptyRequestList() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(emptyRequestsPayload())
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun executeAllowsMultipleRequests() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(multipleStoreRequestsPayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeAcceptsTypeWildcard() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "application/*")
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeIgnoresQZeroAccept() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "${RemoteStoreProtocol.contentType};q=0")
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeIgnoresWildcardWithZeroQ() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "*/*;q=0")
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeAcceptsFallbackAfterZeroQWildcard() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "*/*;q=0, ${RemoteStoreProtocol.contentType};q=0.5")
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeIgnoresInvalidQValue() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "${RemoteStoreProtocol.contentType};q=abc")
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeIgnoresOutOfRangeQValue() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "${RemoteStoreProtocol.contentType};q=1.5")
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeAcceptsUppercaseAcceptWithSpaces() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, " APPLICATION/X-MARYK-PROTOBUF ; Q = 1 ")
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeAcceptsContentTypeWithCharset() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, "${RemoteStoreProtocol.contentType}; charset=utf-8")
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeRejectsInvalidContentLengthHeader() = runBlocking {
        withServer { baseUrl, _ ->
            assertRawStatus(
                baseUrl = baseUrl,
                path = RemoteStoreProtocol.executePath,
                headers = mapOf(
                    HttpHeaders.ContentType to RemoteStoreProtocol.contentType,
                    HttpHeaders.Accept to RemoteStoreProtocol.contentType,
                    HttpHeaders.ContentLength to "abc",
                ),
                expectedStatusCode = 400,
            )
        }
    }

    @Test
    fun executeRejectsNegativeContentLengthHeader() = runBlocking {
        withServer { baseUrl, _ ->
            assertRawStatus(
                baseUrl = baseUrl,
                path = RemoteStoreProtocol.executePath,
                headers = mapOf(
                    HttpHeaders.ContentType to RemoteStoreProtocol.contentType,
                    HttpHeaders.Accept to RemoteStoreProtocol.contentType,
                    HttpHeaders.ContentLength to "-1",
                ),
                expectedStatusCode = 400,
            )
        }
    }

    @Test
    fun flowRejectsMissingContentType() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                setBody(fetchRequestPayload())
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }
    }

    @Test
    fun flowIgnoresUnacceptableAcceptHeader() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(byteArrayOf(9, 9, 9))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowRejectsEmptyPayload() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                setBody(byteArrayOf())
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowRejectsOversizedPayload() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                setBody(oversizedPayload())
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }
    }

    @Test
    fun flowRejectsMalformedPayload() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                setBody(byteArrayOf(9, 9, 9))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowRejectsEmptyRequestList() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                setBody(emptyRequestsPayload())
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowRejectsStoreRequestPayload() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                setBody(storeRequestPayload())
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowRejectsMultipleRequests() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                setBody(multipleFetchRequestsPayload())
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowTypeWildcardFallsThroughToPayloadValidation() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "application/*")
                setBody(byteArrayOf(9, 9, 9))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowIgnoresQZeroAccept() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "${RemoteStoreProtocol.streamContentType};q=0")
                setBody(byteArrayOf(9, 9, 9))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowIgnoresOutOfRangeQValue() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "${RemoteStoreProtocol.streamContentType};q=2")
                setBody(byteArrayOf(9, 9, 9))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowTypeWildcardWithFallbackFallsThroughToPayloadValidation() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "application/*;q=0, ${RemoteStoreProtocol.streamContentType};q=0.3")
                setBody(byteArrayOf(9, 9, 9))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowAcceptsContentTypeWithCharset() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, "${RemoteStoreProtocol.contentType}; charset=utf-8")
                header(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                setBody(byteArrayOf(9, 9, 9))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun flowRejectsInvalidContentLengthHeader() = runBlocking {
        withServer { baseUrl, _ ->
            assertRawStatus(
                baseUrl = baseUrl,
                path = RemoteStoreProtocol.flowPath,
                headers = mapOf(
                    HttpHeaders.ContentType to RemoteStoreProtocol.contentType,
                    HttpHeaders.Accept to RemoteStoreProtocol.streamContentType,
                    HttpHeaders.ContentLength to "nope",
                ),
                expectedStatusCode = 400,
            )
        }
    }

    @Test
    fun flowRejectsNegativeContentLengthHeader() = runBlocking {
        withServer { baseUrl, _ ->
            assertRawStatus(
                baseUrl = baseUrl,
                path = RemoteStoreProtocol.flowPath,
                headers = mapOf(
                    HttpHeaders.ContentType to RemoteStoreProtocol.contentType,
                    HttpHeaders.Accept to RemoteStoreProtocol.streamContentType,
                    HttpHeaders.ContentLength to "-2",
                ),
                expectedStatusCode = 400,
            )
        }
    }

    @Test
    fun processUpdateRejectsMissingContentType() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(validProcessUpdatePayload())
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }
    }

    @Test
    fun processUpdateIgnoresUnacceptableAcceptHeader() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "application/json")
                setBody(byteArrayOf(4, 5, 6))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun processUpdateRejectsEmptyPayload() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(byteArrayOf())
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun processUpdateRejectsOversizedPayload() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(oversizedPayload())
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }
    }

    @Test
    fun processUpdateRejectsMalformedPayload() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(byteArrayOf(4, 5, 6))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun processUpdateTypeWildcardFallsThroughToPayloadValidation() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "application/*")
                setBody(byteArrayOf(4, 5, 6))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun processUpdateIgnoresQZeroAccept() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "${RemoteStoreProtocol.contentType};q=0")
                setBody(byteArrayOf(4, 5, 6))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun processUpdateIgnoresInvalidQValue() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "${RemoteStoreProtocol.contentType};q=oops")
                setBody(byteArrayOf(4, 5, 6))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun processUpdateIgnoresOutOfRangeQValue() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "${RemoteStoreProtocol.contentType};q=9")
                setBody(byteArrayOf(4, 5, 6))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun processUpdateTypeWildcardWithFallbackFallsThroughToPayloadValidation() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, "application/*;q=0, ${RemoteStoreProtocol.contentType};q=0.2")
                setBody(byteArrayOf(4, 5, 6))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun processUpdateAcceptsContentTypeWithCharset() = runBlocking {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, "${RemoteStoreProtocol.contentType}; charset=utf-8")
                header(HttpHeaders.Accept, "${RemoteStoreProtocol.contentType};q=1")
                setBody(byteArrayOf(4, 5, 6))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun processUpdateRejectsInvalidContentLengthHeader() = runBlocking {
        withServer { baseUrl, _ ->
            assertRawStatus(
                baseUrl = baseUrl,
                path = RemoteStoreProtocol.processUpdatePath,
                headers = mapOf(
                    HttpHeaders.ContentType to RemoteStoreProtocol.contentType,
                    HttpHeaders.Accept to RemoteStoreProtocol.contentType,
                    HttpHeaders.ContentLength to "invalid",
                ),
                expectedStatusCode = 400,
            )
        }
    }

    @Test
    fun processUpdateRejectsNegativeContentLengthHeader() = runBlocking {
        withServer { baseUrl, _ ->
            assertRawStatus(
                baseUrl = baseUrl,
                path = RemoteStoreProtocol.processUpdatePath,
                headers = mapOf(
                    HttpHeaders.ContentType to RemoteStoreProtocol.contentType,
                    HttpHeaders.Accept to RemoteStoreProtocol.contentType,
                    HttpHeaders.ContentLength to "-3",
                ),
                expectedStatusCode = 400,
            )
        }
    }
}

private suspend fun withServer(block: suspend (String, HttpClient) -> Unit) {
    val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
    val port = ServerSocket(0).use { it.localPort }
    val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
        remoteStoreModule(store)
    }.start(wait = false)
    val client = HttpClient(CIO) { expectSuccess = false }
    try {
        block("http://127.0.0.1:$port", client)
    } finally {
        client.close()
        server.stop(500, 500)
        store.close()
    }
}

private fun emptyRequestsPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(emptyList()),
        testRequestContext(),
    )

private fun fetchRequestPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))),
        testRequestContext(),
    )

private fun storeRequestPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            SimpleMarykModel.add(
                SimpleMarykModel.create {
                    value with "x"
                }
            )
        ),
        testRequestContext(),
    )

private fun validExecutePayload(): ByteArray = storeRequestPayload()

private fun multipleStoreRequestsPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            listOf(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "a" }),
                SimpleMarykModel.add(SimpleMarykModel.create { value with "b" }),
            )
        ),
        testRequestContext(),
    )

private fun multipleFetchRequestsPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            listOf(
                SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16))),
                SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16))),
            )
        ),
        testRequestContext(),
    )

private fun validProcessUpdatePayload(): ByteArray =
    RemoteStoreCodec.encode(
        UpdateResponse.Serializer,
        UpdateResponse(
            dataModel = SimpleMarykModel,
            update = OrderedKeysUpdate(
                keys = listOf(SimpleMarykModel.key(ByteArray(16))),
                version = 1uL,
            ),
        ),
        testRequestContext(),
    )

private fun testRequestContext(): RequestContext =
    RequestContext(
        DefinitionsContext(
            dataModels = mutableMapOf(
                SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
            )
        ),
        dataModel = SimpleMarykModel,
    )

private fun oversizedPayload(): ByteArray = ByteArray((16 * 1024 * 1024) + 1) { 0x01 }

private fun assertRawStatus(
    baseUrl: String,
    path: String,
    headers: Map<String, String>,
    expectedStatusCode: Int,
) {
    val port = baseUrl.substringAfterLast(':').toInt()
    Socket("127.0.0.1", port).use { socket ->
        val request = buildString {
            append("POST $path HTTP/1.1\r\n")
            append("Host: 127.0.0.1:$port\r\n")
            headers.forEach { (name, value) ->
                append("$name: $value\r\n")
            }
            append("Connection: close\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(request.encodeToByteArray())
        socket.getOutputStream().flush()

        val statusLine = socket.getInputStream()
            .bufferedReader()
            .readLine()
        val statusCode = statusLine
            ?.split(' ')
            ?.getOrNull(1)
            ?.toIntOrNull()
        assertEquals(expectedStatusCode, statusCode)
    }
}

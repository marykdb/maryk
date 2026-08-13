package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.utils.io.readRemaining
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.io.readByteArray
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import maryk.core.models.key
import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationRuntimeState
import maryk.core.models.migration.MigrationRuntimeStatus
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.requests.CollectRequest
import maryk.core.query.requests.RequestType
import maryk.core.query.requests.Requests
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.migration.MigrationAdmin
import maryk.test.models.SimpleMarykModel

class RemoteStoreServerTest {
    @Test
    fun rejectsUnauthenticatedPublicBindingByDefault() {
        val exception = assertFailsWith<IllegalArgumentException> {
            validateRemoteStoreServerBinding("0.0.0.0", RemoteStoreServerConfig())
        }

        assertTrue(exception.message.orEmpty().contains("non-loopback"))
    }

    @Test
    fun rejectsProtectedPublicBindingWithoutExplicitInsecureOptIn() {
        listOf(
            RemoteStoreServerConfig(bearerToken = "secret"),
            RemoteStoreServerConfig(
                authenticator = RemoteStoreAuthenticator { RemoteStorePrincipal("service") }
            ),
        ).forEach { config ->
            val exception = assertFailsWith<IllegalArgumentException> {
                validateRemoteStoreServerBinding("0.0.0.0", config)
            }

            assertTrue(exception.message.orEmpty().contains("plaintext"))
        }
    }

    @Test
    fun acceptsLoopbackOrExplicitPublicInsecureOptIn() {
        validateRemoteStoreServerBinding("127.0.0.1", RemoteStoreServerConfig())
        validateRemoteStoreServerBinding("localhost", RemoteStoreServerConfig())
        validateRemoteStoreServerBinding("::1", RemoteStoreServerConfig())
        validateRemoteStoreServerBinding("[::1]", RemoteStoreServerConfig())
        validateRemoteStoreServerBinding("0:0:0:0:0:0:0:1", RemoteStoreServerConfig())
        validateRemoteStoreServerBinding(
            "0.0.0.0",
            RemoteStoreServerConfig(allowInsecureRemoteBinding = true),
        )
    }

    @Test
    fun rejectsRequestsWhenCallAdmissionIsExhausted() = runBoundedIntegrationTest {
        withServer(limits = RemoteStoreServerLimits(maxConcurrentCalls = 0)) { baseUrl, client ->
            val response = client.get("$baseUrl${RemoteStoreProtocol.infoPath}")

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("Remote Store is at call capacity", response.bodyAsText())
        }
    }

    @Test
    fun rejectsFlowsWhenFlowAdmissionIsExhausted() = runBoundedIntegrationTest {
        withServer(limits = RemoteStoreServerLimits(maxConcurrentFlows = 0)) { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(fetchRequestPayload())
            }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("Remote Store is at flow capacity", response.bodyAsText())
        }
    }

    @Test
    fun acceptsPositiveAdmissionLimits() {
        validateRemoteStoreServerBinding(
            "127.0.0.1",
            RemoteStoreServerConfig(),
            RemoteStoreServerLimits(maxConcurrentCalls = 1, maxConcurrentFlows = 1),
        )
    }

    @Test
    fun rejectsNonPositiveConnectionIdleTimeout() {
        val exception = assertFailsWith<IllegalArgumentException> {
            validateRemoteStoreServerBinding(
                "127.0.0.1",
                RemoteStoreServerConfig(),
                RemoteStoreServerLimits(connectionIdleTimeoutSeconds = 0),
            )
        }

        assertTrue(exception.message.orEmpty().contains("connection idle timeout"))
    }

    @Test
    fun flowKeepsCallAndFlowPermitsUntilClientCancellation() = runBoundedIntegrationTest {
        withServer(
            config = RemoteStoreServerConfig(flowHeartbeatMillis = 50),
            limits = RemoteStoreServerLimits(maxConcurrentCalls = 1, maxConcurrentFlows = 1),
        ) { baseUrl, client ->
            val socket = openRawFlow(baseUrl, resumable = true)
            try {
                withTimeout(2.seconds) {
                    while (true) {
                        val status = flowStatus(client, baseUrl)
                        if (status == HttpStatusCode.ServiceUnavailable) break
                        assertEquals(HttpStatusCode.OK, status)
                        delay(10)
                    }
                }

                socket.close()

                withTimeout(2.seconds) {
                    while (true) {
                        val status = flowStatus(client, baseUrl)
                        if (status == HttpStatusCode.OK) {
                            break
                        }
                        assertEquals(HttpStatusCode.ServiceUnavailable, status)
                        delay(10)
                    }
                }
            } finally {
                socket.close()
            }
        }
    }

    @Test
    fun executeTimesOutAnIncompleteRequestBody() = runBoundedIntegrationTest {
        withServer(limits = RemoteStoreServerLimits(requestBodyReadTimeoutMillis = 50)) { baseUrl, _ ->
            assertRawStatus(
                baseUrl = baseUrl,
                path = RemoteStoreProtocol.executePath,
                headers = mapOf(
                    HttpHeaders.ContentType to RemoteStoreProtocol.contentType,
                    HttpHeaders.ContentLength to "1",
                ),
                expectedStatusCode = HttpStatusCode.RequestTimeout.value,
            )
        }
    }

    @Test
    fun rejectsBlankBearerToken() {
        val exception = assertFailsWith<IllegalArgumentException> {
            validateRemoteStoreServerBinding(
                "127.0.0.1",
                RemoteStoreServerConfig(bearerToken = " "),
            )
        }

        assertTrue(exception.message.orEmpty().contains("cannot be blank"))
    }

    @Test
    fun bearerAuthenticationProtectsEveryEndpointBeforeValidation() = runBoundedIntegrationTest {
        withServer(RemoteStoreServerConfig(bearerToken = "secret")) { baseUrl, client ->
            listOf(
                RemoteStoreProtocol.infoPath to false,
                RemoteStoreProtocol.snapshotVersionPath to false,
                RemoteStoreProtocol.executePath to true,
                RemoteStoreProtocol.flowPath to true,
                RemoteStoreProtocol.processUpdatePath to true,
                RemoteStoreProtocol.migrationsPath to true,
            ).forEach { (path, post) ->
                val missingResponse = if (post) client.post("$baseUrl$path") else client.get("$baseUrl$path")
                assertEquals(HttpStatusCode.Unauthorized, missingResponse.status, path)

                val wrongResponse = if (post) {
                    client.post("$baseUrl$path") {
                        header(HttpHeaders.Authorization, "Bearer wrong")
                    }
                } else {
                    client.get("$baseUrl$path") {
                        header(HttpHeaders.Authorization, "Bearer wrong")
                    }
                }
                assertEquals(HttpStatusCode.Unauthorized, wrongResponse.status, path)
            }

            val response = client.get("$baseUrl${RemoteStoreProtocol.infoPath}") {
                header(HttpHeaders.Authorization, "Bearer secret")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun migrationAdministrationRoundTripsThroughRemoteClient() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val adminStore = TestMigrationAdminStore(store)
        val (server, port) = startTestServer { remoteStoreModule(adminStore) }
        val client = HttpClient(CIO) { expectSuccess = false }
        try {
            val remote = RemoteDataStore.connect(
                RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port", httpClient = client)
            )
            assertEquals(MigrationRuntimeState.Running, remote.getMigrationStatuses().getValue(1u).state)
            assertEquals(2u, remote.getMigrationMetrics().getValue(1u).retries)
            assertTrue(remote.requestMigrationPause(1u))
            assertEquals(1u, adminStore.pausedModelId)
            assertTrue(remote.requestMigrationResume(1u))
            assertEquals(1u, adminStore.resumedModelId)
            assertTrue(remote.requestMigrationCancel(1u, "operator maintenance"))
            assertEquals(1u, adminStore.canceledModelId)
            assertEquals("operator maintenance", adminStore.cancelReason)
            remote.close()
        } finally {
            client.close()
            server.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun migrationAdministrationHonorsAuthorizer() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val adminStore = TestMigrationAdminStore(store)
        val (server, port) = startTestServer {
            remoteStoreModule(
                adminStore,
                RemoteStoreServerConfig(
                    authorizer = RemoteStoreAuthorizer { request ->
                        request.operation == RemoteStoreOperation.Info ||
                            request.operation == RemoteStoreOperation.MigrationStatus
                    },
                ),
            )
        }
        val client = HttpClient(CIO) { expectSuccess = false }
        try {
            val remote = RemoteDataStore.connect(
                RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port", httpClient = client)
            )
            assertEquals(MigrationRuntimeState.Running, remote.getMigrationSnapshot().statuses.getValue(1u).state)
            val error = assertFailsWith<IllegalStateException> {
                remote.requestMigrationPause(1u)
            }
            assertTrue(error.message.orEmpty().contains("HTTP 403"))
            remote.close()
        } finally {
            client.close()
            server.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun migrationAdministrationRejectsOversizedResponses() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val adminStore = TestMigrationAdminStore(
            store,
            statusMessage = "x".repeat(12 * 1024 * 1024),
        )
        val (server, port) = startTestServer { remoteStoreModule(adminStore) }
        val client = HttpClient(CIO) { expectSuccess = false }
        try {
            val response = client.post("http://127.0.0.1:$port${RemoteStoreProtocol.migrationsPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(RemoteMigrationAdminCodec.encodeRequest(RemoteMigrationRequest(RemoteMigrationOperation.Status)))
            }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertEquals(
                "Remote migration administration response exceeds max size: limit is 16777216 bytes",
                response.bodyAsText(),
            )
        } finally {
            client.close()
            server.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun customAuthenticatorAndAuthorizerProtectInfo() = runBoundedIntegrationTest {
        val config = RemoteStoreServerConfig(
            authenticator = RemoteStoreAuthenticator { header ->
                if (header == "ApiKey accepted") RemoteStorePrincipal("reporter") else null
            },
            authorizer = RemoteStoreAuthorizer { request ->
                request.principal.id == "reporter" &&
                    request.operation == RemoteStoreOperation.Info
            },
        )
        withServer(config) { baseUrl, client ->
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("$baseUrl${RemoteStoreProtocol.infoPath}").status,
            )
            assertEquals(
                HttpStatusCode.OK,
                client.get("$baseUrl${RemoteStoreProtocol.infoPath}") {
                    header(HttpHeaders.Authorization, "ApiKey accepted")
                }.status,
            )
        }
    }

    @Test
    fun infoAllowsArbitraryAcceptHeader() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.get("$baseUrl${RemoteStoreProtocol.infoPath}") {
                header(HttpHeaders.Accept, "application/json")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun infoAcceptsTypeWildcard() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.get("$baseUrl${RemoteStoreProtocol.infoPath}") {
                header(HttpHeaders.Accept, "application/*")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeRejectsMissingContentType() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }
    }

    @Test
    fun executeRejectsWrongContentType() = runBoundedIntegrationTest {
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
    fun executeAllowsArbitraryAcceptHeader() = runBoundedIntegrationTest {
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
    fun executeRejectsEmptyPayload() = runBoundedIntegrationTest {
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
    fun executeRejectsOversizedPayload() = runBoundedIntegrationTest {
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
    fun executeRejectsMalformedPayload() = runBoundedIntegrationTest {
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
    fun executeRejectsEmptyRequestList() = runBoundedIntegrationTest {
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
    fun executeAllowsMultipleRequests() = runBoundedIntegrationTest {
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
    fun executeKeepsLegacySingleResponseUnframed() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(validExecutePayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val payload = response.bodyAsChannel().readRemaining().readByteArray()
            val decoded = RemoteStoreCodec.decode(
                AddResponse.Serializer,
                payload,
                testRequestContext(),
            )
            assertEquals(1, decoded.statuses.size)
        }
    }

    @Test
    fun executeAcceptsCollectRequest() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(collectStoreRequestPayload())
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun executeAcceptsTypeWildcard() = runBoundedIntegrationTest {
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
    fun executeIgnoresQZeroAccept() = runBoundedIntegrationTest {
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
    fun executeIgnoresWildcardWithZeroQ() = runBoundedIntegrationTest {
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
    fun executeAcceptsFallbackAfterZeroQWildcard() = runBoundedIntegrationTest {
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
    fun executeIgnoresInvalidQValue() = runBoundedIntegrationTest {
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
    fun executeIgnoresOutOfRangeQValue() = runBoundedIntegrationTest {
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
    fun executeAcceptsUppercaseAcceptWithSpaces() = runBoundedIntegrationTest {
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
    fun executeAcceptsContentTypeWithCharset() = runBoundedIntegrationTest {
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
    fun executeRejectsInvalidContentLengthHeader() = runBoundedIntegrationTest {
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
    fun executeRejectsNegativeContentLengthHeader() = runBoundedIntegrationTest {
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
    fun flowRejectsMissingContentType() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                setBody(fetchRequestPayload())
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }
    }

    @Test
    fun flowIgnoresUnacceptableAcceptHeader() = runBoundedIntegrationTest {
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
    fun flowRejectsEmptyPayload() = runBoundedIntegrationTest {
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
    fun flowRejectsOversizedPayload() = runBoundedIntegrationTest {
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
    fun flowRejectsMalformedPayload() = runBoundedIntegrationTest {
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
    fun flowRejectsEmptyRequestList() = runBoundedIntegrationTest {
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
    fun flowRejectsStoreRequestPayload() = runBoundedIntegrationTest {
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
    fun flowRejectsMultipleRequests() = runBoundedIntegrationTest {
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
    fun flowTypeWildcardFallsThroughToPayloadValidation() = runBoundedIntegrationTest {
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
    fun flowIgnoresQZeroAccept() = runBoundedIntegrationTest {
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
    fun flowIgnoresOutOfRangeQValue() = runBoundedIntegrationTest {
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
    fun flowTypeWildcardWithFallbackFallsThroughToPayloadValidation() = runBoundedIntegrationTest {
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
    fun flowAcceptsContentTypeWithCharset() = runBoundedIntegrationTest {
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
    fun flowRejectsInvalidContentLengthHeader() = runBoundedIntegrationTest {
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
    fun flowRejectsNegativeContentLengthHeader() = runBoundedIntegrationTest {
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
    fun processUpdateRejectsMissingContentType() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(validProcessUpdatePayload())
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }
    }

    @Test
    fun processUpdateIgnoresUnacceptableAcceptHeader() = runBoundedIntegrationTest {
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
    fun processUpdateRejectsEmptyPayload() = runBoundedIntegrationTest {
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
    fun processUpdateRejectsOversizedPayload() = runBoundedIntegrationTest {
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
    fun processUpdateRejectsMalformedPayload() = runBoundedIntegrationTest {
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
    fun processUpdateInitialChangesHonorsAddAuthorization() = runBoundedIntegrationTest {
        withServer(
            RemoteStoreServerConfig(
                authorizer = RemoteStoreAuthorizer { request -> request.requestType != RequestType.Add },
            )
        ) { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(initialChangesWithCreatePayload())
            }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun processUpdateTypeWildcardFallsThroughToPayloadValidation() = runBoundedIntegrationTest {
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
    fun processUpdateIgnoresQZeroAccept() = runBoundedIntegrationTest {
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
    fun processUpdateIgnoresInvalidQValue() = runBoundedIntegrationTest {
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
    fun processUpdateIgnoresOutOfRangeQValue() = runBoundedIntegrationTest {
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
    fun processUpdateTypeWildcardWithFallbackFallsThroughToPayloadValidation() = runBoundedIntegrationTest {
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
    fun processUpdateAcceptsContentTypeWithCharset() = runBoundedIntegrationTest {
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
    fun processUpdateRejectsInvalidContentLengthHeader() = runBoundedIntegrationTest {
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
    fun processUpdateRejectsNegativeContentLengthHeader() = runBoundedIntegrationTest {
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

private class TestMigrationAdminStore(
    private val delegate: IsDataStore,
    private val statusMessage: String? = null,
) : IsDataStore by delegate, MigrationAdmin {
    var pausedModelId: UInt? = null
    var resumedModelId: UInt? = null
    var canceledModelId: UInt? = null
    var cancelReason: String? = null

    override suspend fun getMigrationStatuses(): Map<UInt, MigrationRuntimeStatus> =
        mapOf(1u to MigrationRuntimeStatus(MigrationRuntimeState.Running, message = statusMessage))

    override suspend fun getMigrationMetrics(): Map<UInt, MigrationMetrics> =
        mapOf(1u to MigrationMetrics(retries = 2u))

    override suspend fun requestMigrationPause(modelId: UInt): Boolean {
        pausedModelId = modelId
        return true
    }

    override suspend fun requestMigrationResume(modelId: UInt): Boolean {
        resumedModelId = modelId
        return true
    }

    override suspend fun requestMigrationCancel(modelId: UInt, reason: String): Boolean {
        canceledModelId = modelId
        cancelReason = reason
        return true
    }
}

private suspend fun withServer(
    config: RemoteStoreServerConfig = RemoteStoreServerConfig(),
    limits: RemoteStoreServerLimits = RemoteStoreServerLimits(),
    block: suspend (String, HttpClient) -> Unit,
) {
    val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
    val (server, port) = startTestServer {
        remoteStoreModule(store, config, limits)
    }
    val client = HttpClient(CIO) { expectSuccess = false }
    try {
        block("http://127.0.0.1:$port", client)
    } finally {
        client.close()
        server.stop(500, 500)
        store.close()
    }
}

private suspend fun startTestServer(
    module: suspend io.ktor.server.application.Application.() -> Unit
): Pair<EmbeddedServer<*, *>, Int> {
    val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0, module = module).start(wait = false)
    val port = server.engine.resolvedConnectors().single().port
    return server to port
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

private fun collectStoreRequestPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            CollectRequest(
                "created",
                SimpleMarykModel.add(SimpleMarykModel.create { value with "collected" }),
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

private fun initialChangesWithCreatePayload(): ByteArray =
    RemoteStoreCodec.encode(
        UpdateResponse.Serializer,
        UpdateResponse(
            dataModel = SimpleMarykModel,
            update = InitialChangesUpdate(
                version = 1uL,
                changes = listOf(
                    DataObjectVersionedChange(
                        key = SimpleMarykModel.key(ByteArray(16)),
                        changes = listOf(
                            VersionedChanges(
                                version = 1uL,
                                changes = listOf(ObjectCreate),
                            )
                        ),
                    )
                ),
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

private fun openRawFlow(baseUrl: String, resumable: Boolean = false): Socket {
    val port = baseUrl.substringAfterLast(':').toInt()
    val payload = fetchRequestPayload()
    return Socket("127.0.0.1", port).also { socket ->
        val request = buildString {
            append("POST ${RemoteStoreProtocol.flowPath} HTTP/1.1\r\n")
            append("Host: 127.0.0.1:$port\r\n")
            append("${HttpHeaders.ContentType}: ${RemoteStoreProtocol.contentType}\r\n")
            append("${HttpHeaders.ContentLength}: ${payload.size}\r\n")
            if (resumable) {
                append("${RemoteStoreProtocol.flowProtocolHeader}: ${RemoteStoreProtocol.resumableFlowProtocol}\r\n")
            }
            append("\r\n")
        }.encodeToByteArray()
        socket.getOutputStream().write(request)
        socket.getOutputStream().write(payload)
        socket.getOutputStream().flush()
    }
}

private suspend fun flowStatus(client: HttpClient, baseUrl: String): HttpStatusCode =
    client.preparePost("$baseUrl${RemoteStoreProtocol.flowPath}") {
        header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
        setBody(fetchRequestPayload())
    }.execute { response -> response.status }

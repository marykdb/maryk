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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.io.readByteArray
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.metric.ValueCount
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationRuntimeState
import maryk.core.models.migration.MigrationRuntimeStatus
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.Matches
import maryk.core.query.pairs.with
import maryk.core.query.requests.CollectRequest
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.RequestType
import maryk.core.query.requests.Requests
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.migration.MigrationAdmin
import maryk.test.models.CompleteMarykModel
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
    fun executeMapsDatastoreRequestExceptionToBadRequest() = runBoundedIntegrationTest {
        val delegate = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        withServer(dataStore = RequestExceptionStore(delegate)) { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(validExecutePayload())
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun executeReportsCompletedBatchPrefixWhenLaterRequestIsRejected() = runBoundedIntegrationTest {
        val delegate = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        withServer(dataStore = FailSecondExecuteStore(delegate)) { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(multipleStoreRequestsPayload())
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("1", response.headers[RemoteStoreProtocol.completedRequestCountHeader])
        }
    }

    @Test
    fun executeReportsCompletedBatchPrefixWhenLaterReadEncodingFails() = runBoundedIntegrationTest {
        val delegate = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        withServer(dataStore = MismatchedSecondResponseStore(delegate)) { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(addThenGetPayload())
            }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("1", response.headers[RemoteStoreProtocol.completedRequestCountHeader])
        }
    }

    @Test
    fun executeCountsCurrentMutationWhenBatchResponseLimitIsExceeded() = runBoundedIntegrationTest {
        val delegate = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val executions = AtomicInteger()
        withServer(dataStore = LargeAddResponseStore(delegate, executions)) { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(RemoteStoreCodec.encode(
                    Requests.Serializer,
                    Requests(List(7) { index ->
                        SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-batch$index" })
                    }),
                    testRequestContext(),
                ))
            }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertEquals(6, executions.get())
            assertEquals("6", response.headers[RemoteStoreProtocol.completedRequestCountHeader])
        }
    }

    @Test
    fun executeClosesConnectionWhenRejectingUnreadBody() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, "text/plain")
                setBody(byteArrayOf(1))
            }

            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
            assertEquals("close", response.headers[HttpHeaders.Connection])
        }
    }

    @Test
    fun executeMarksMutationOutcomeUnknownWhenResponseEncodingFails() = runBoundedIntegrationTest {
        val delegate = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val executions = AtomicInteger()
        withServer(dataStore = MismatchedResponseStore(delegate, executions)) { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(validExecutePayload())
            }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("unknown", response.headers[RemoteStoreProtocol.mutationOutcomeHeader])
            assertEquals(1, executions.get())
        }
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
    fun flowReauthenticatesCustomIdentityOnHeartbeat() = runBoundedIntegrationTest {
        val authenticationAttempts = AtomicInteger()
        withServer(
            config = RemoteStoreServerConfig(
                flowHeartbeatMillis = 50,
                authenticator = RemoteStoreAuthenticator {
                    authenticationAttempts.incrementAndGet()
                    RemoteStorePrincipal("service")
                },
            ),
        ) { baseUrl, _ ->
            val socket = openRawFlow(baseUrl, resumable = true)
            try {
                withTimeout(2.seconds) {
                    while (authenticationAttempts.get() < 2) delay(10)
                }
            } finally {
                socket.close()
            }
        }
    }

    @Test
    fun flowDoesNotBufferManyLargeUpdateFramesAheadOfSlowClient() = runBoundedIntegrationTest {
        val delegate = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val emitted = AtomicInteger()
        val store = FastLargeFlowStore(delegate, emitted)
        val (server, port) = startTestServer { remoteStoreModule(store) }
        val socket = openRawFlow("http://127.0.0.1:$port")
        try {
            withTimeout(2.seconds) {
                while (emitted.get() == 0) delay(10)
            }
            delay(250)

            assertTrue(
                // CIO retains up to eight queued MiB frames, while the response writer can hold
                // one frame that is currently being flushed. The upstream flow must remain bounded
                // by those nine frames rather than draining the source for a stalled socket.
                emitted.get() <= 9,
                "Remote flow buffered ${emitted.get()} large updates ahead of a client that read none",
            )
        } finally {
            socket.close()
            server.stop(500, 500)
            store.close()
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

            val caseInsensitiveSchemeResponse = client.get("$baseUrl${RemoteStoreProtocol.infoPath}") {
                header(HttpHeaders.Authorization, "bEaReR secret")
            }
            assertEquals(HttpStatusCode.OK, caseInsensitiveSchemeResponse.status)

            val caseSensitiveTokenResponse = client.get("$baseUrl${RemoteStoreProtocol.infoPath}") {
                header(HttpHeaders.Authorization, "bearer SECRET")
            }
            assertEquals(HttpStatusCode.Unauthorized, caseSensitiveTokenResponse.status)
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
    fun executeRejectsMoreThan256Requests() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(tooManyStoreRequestsPayload())
            }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertTrue(response.bodyAsText().contains("request count"))
        }
    }

    @Test
    fun executeRejectsMoreThan1024FilterWorkUnits() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(filterWorkPayload())
            }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertTrue(response.bodyAsText().contains("filter work"))
        }
    }

    @Test
    fun executeRejectsMoreThan128Aggregations() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(aggregationWorkPayload())
            }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertTrue(response.bodyAsText().contains("aggregation count"))
        }
    }

    @Test
    fun executeDoesNotAuthorizeReferenceTargetWhenFilterOnlyComparesItsKey() = runBoundedIntegrationTest {
        val authorizedModels = mutableListOf<String?>()
        withServer(
            config = RemoteStoreServerConfig(
                authorizer = RemoteStoreAuthorizer { request ->
                    authorizedModels += request.modelName
                    request.modelName != NamedIndexTargetModel.Meta.name
                },
            ),
            dataModelsById = mapOf(
                1u to NamedIndexSourceModel,
                2u to NamedIndexTargetModel,
            ),
        ) { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(terminalReferenceFilterPayload())
            }

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        }

        assertEquals(listOf<String?>(NamedIndexSourceModel.Meta.name), authorizedModels)
    }

    @Test
    fun flowDoesNotAuthorizeReferenceTargetWhenFilterOnlyComparesItsKey() = runBoundedIntegrationTest {
        val authorizedModels = mutableListOf<String?>()
        withServer(
            config = RemoteStoreServerConfig(
                authorizer = RemoteStoreAuthorizer { request ->
                    authorizedModels += request.modelName
                    request.modelName != NamedIndexTargetModel.Meta.name
                },
            ),
            dataModelsById = mapOf(
                1u to NamedIndexSourceModel,
                2u to NamedIndexTargetModel,
            ),
        ) { baseUrl, client ->
            val status = client.preparePost("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(terminalReferenceFilterFlowPayload())
            }.execute { response -> response.status.also { response.call.cancel() } }

            assertEquals(HttpStatusCode.OK, status)
        }

        assertEquals(
            listOf<String?>(NamedIndexSourceModel.Meta.name, NamedIndexSourceModel.Meta.name),
            authorizedModels,
        )
    }

    @Test
    fun executePreflightsLaterAuthorizationBeforeMutatingBatchPrefix() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(
            dataModelsById = mapOf(
                1u to SimpleMarykModel,
                2u to CompleteMarykModel,
            )
        )
        val config = RemoteStoreServerConfig(
            authorizer = RemoteStoreAuthorizer { request ->
                request.modelName != CompleteMarykModel.Meta.name
            }
        )
        val (server, port) = startTestServer { remoteStoreModule(store, config) }
        val client = HttpClient(CIO) { expectSuccess = false }
        try {
            val response = client.post("http://127.0.0.1:$port${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(addThenDeniedScanPayload())
            }

            assertEquals(HttpStatusCode.Forbidden, response.status, response.bodyAsText())
            val stored = store.execute(SimpleMarykModel.scan(allowTableScan = true))
            assertTrue(stored.values.isEmpty())
        } finally {
            client.close()
            server.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun executePreflightsLaterWorkLimitBeforeMutatingBatchPrefix() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val (server, port) = startTestServer { remoteStoreModule(store) }
        val client = HttpClient(CIO) { expectSuccess = false }
        try {
            val response = client.post("http://127.0.0.1:$port${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(addThenOversizedFilterPayload())
            }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status, response.bodyAsText())
            val stored = store.execute(SimpleMarykModel.scan(allowTableScan = true))
            assertTrue(stored.values.isEmpty())
        } finally {
            client.close()
            server.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun executeDeniesNamedIndexWhichMayTraverseUnauthorizedModel() = runBoundedIntegrationTest {
        val authorizedModels = mutableListOf<String?>()
        withServer(
            config = RemoteStoreServerConfig(
                authorizer = RemoteStoreAuthorizer { request ->
                    authorizedModels += request.modelName
                    request.modelName != NamedIndexTargetModel.Meta.name
                }
            ),
            dataModelsById = mapOf(
                1u to NamedIndexSourceModel,
                2u to NamedIndexTargetModel,
            ),
        ) { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(crossModelNamedIndexPayload())
            }

            assertEquals(HttpStatusCode.Forbidden, response.status, response.bodyAsText())
        }

        assertEquals(
            listOf<String?>(NamedIndexSourceModel.Meta.name, NamedIndexTargetModel.Meta.name),
            authorizedModels,
        )
    }

    @Test
    fun flowDeniesNamedIndexWhichMayTraverseUnauthorizedModel() = runBoundedIntegrationTest {
        val authorizedModels = mutableListOf<String?>()
        withServer(
            config = RemoteStoreServerConfig(
                authorizer = RemoteStoreAuthorizer { request ->
                    authorizedModels += request.modelName
                    request.modelName != NamedIndexTargetModel.Meta.name
                }
            ),
            dataModelsById = mapOf(
                1u to NamedIndexSourceModel,
                2u to NamedIndexTargetModel,
            ),
        ) { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(crossModelNamedIndexFlowPayload())
            }

            assertEquals(HttpStatusCode.Forbidden, response.status, response.bodyAsText())
        }

        assertEquals(
            listOf<String?>(NamedIndexSourceModel.Meta.name, NamedIndexTargetModel.Meta.name),
            authorizedModels,
        )
    }

    @Test
    fun executeDeniesUnknownNamedSearchIndex() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.executePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(unknownNamedIndexPayload())
            }

            assertEquals(HttpStatusCode.Forbidden, response.status, response.bodyAsText())
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
    fun flowRejectsMoreThan1024FilterWorkUnits() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.flowPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(filterWorkPayload())
            }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertTrue(response.bodyAsText().contains("filter work"))
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
    fun processUpdateRejectsVersionWithoutTwoSafeSuccessors() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val (server, port) = startTestServer { remoteStoreModule(store) }
        val client = HttpClient(CIO) { expectSuccess = false }
        try {
            val response = client.post("http://127.0.0.1:$port${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(unsafeAdditionProcessUpdatePayload())
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val rejected = store.execute(SimpleMarykModel.get(rejectedProcessUpdateKey()))
            assertTrue(rejected.values.isEmpty())
            val add = store.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-after-rejection" })
            )
            assertTrue(add.statuses.single() is AddSuccess<*>)
        } finally {
            client.close()
            server.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun processUpdateRejectsUnsafeNestedVersion() = runBoundedIntegrationTest {
        withServer { baseUrl, client ->
            val response = client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                setBody(initialChangesWithCreatePayload(ULong.MAX_VALUE))
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
    fun processUpdateMixedChangeRequiresChangeAndDeleteAuthorization() = runBoundedIntegrationTest {
        val authorizedRequestTypes = mutableListOf<RequestType?>()
        withServer(
            RemoteStoreServerConfig(
                authorizer = RemoteStoreAuthorizer { request ->
                    authorizedRequestTypes += request.requestType
                    request.requestType != RequestType.Delete
                },
            )
        ) { baseUrl, client ->
            client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(changeWithSoftDeletePayload())
            }
        }

        assertEquals(listOf<RequestType?>(RequestType.Change, RequestType.Delete), authorizedRequestTypes)
    }

    @Test
    fun processUpdateSoftDeleteChangeRequiresDeleteAuthorization() = runBoundedIntegrationTest {
        val authorizedRequestTypes = mutableListOf<RequestType?>()
        withServer(
            RemoteStoreServerConfig(
                authorizer = RemoteStoreAuthorizer { request ->
                    authorizedRequestTypes += request.requestType
                    request.requestType != RequestType.Delete
                },
            )
        ) { baseUrl, client ->
            client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(softDeleteChangePayload())
            }
        }

        assertEquals(listOf<RequestType?>(RequestType.Delete), authorizedRequestTypes)
    }

    @Test
    fun processUpdateRestoreChangeRequiresChangeAuthorization() = runBoundedIntegrationTest {
        val authorizedRequestTypes = mutableListOf<RequestType?>()
        withServer(
            RemoteStoreServerConfig(
                authorizer = RemoteStoreAuthorizer { request ->
                    authorizedRequestTypes += request.requestType
                    request.requestType != RequestType.Change
                },
            )
        ) { baseUrl, client ->
            client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(restoreChangePayload())
            }
        }

        assertEquals(listOf<RequestType?>(RequestType.Change), authorizedRequestTypes)
    }

    @Test
    fun processUpdateMixedInitialChangesRequiresAddChangeAndDeleteAuthorization() = runBoundedIntegrationTest {
        val authorizedRequestTypes = mutableListOf<RequestType?>()
        withServer(
            RemoteStoreServerConfig(
                authorizer = RemoteStoreAuthorizer { request ->
                    authorizedRequestTypes += request.requestType
                    request.requestType != RequestType.Delete
                },
            )
        ) { baseUrl, client ->
            client.post("$baseUrl${RemoteStoreProtocol.processUpdatePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                setBody(initialChangesWithMixedOperationsPayload())
            }
        }

        assertEquals(
            listOf<RequestType?>(RequestType.Add, RequestType.Change, RequestType.Delete),
            authorizedRequestTypes,
        )
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

private class FastLargeFlowStore(
    private val delegate: IsDataStore,
    private val emitted: AtomicInteger,
) : IsDataStore by delegate {
    override suspend fun <DM : IsRootDataModel, RQ, RP> executeFlow(
        request: RQ,
    ): Flow<IsUpdateResponse<DM>> where RQ : IsFlowRequest<DM, RP>,
                                      RP : IsDataResponse<DM> = flow {
        val sortingKey = Bytes(ByteArray(1024 * 1024))
        repeat(100) { index ->
            emitted.incrementAndGet()
            emit(
                OrderedKeysUpdate(
                    keys = emptyList(),
                    version = index.toULong() + 1uL,
                    sortingKeys = listOf(sortingKey),
                )
            )
        }
    }
}

private class RequestExceptionStore(
    private val delegate: IsDataStore,
) : IsDataStore by delegate {
    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ,
    ): RP = throw RequestException("Rejected by datastore")
}

private class FailSecondExecuteStore(
    private val delegate: IsDataStore,
) : IsDataStore by delegate {
    private var executions = 0

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ,
    ): RP {
        executions++
        if (executions == 2) throw RequestException("Second request rejected")
        return delegate.execute(request)
    }
}

private class MismatchedResponseStore(
    private val delegate: IsDataStore,
    private val executions: AtomicInteger,
) : IsDataStore by delegate {
    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ,
    ): RP {
        delegate.execute(request)
        executions.incrementAndGet()
        @Suppress("UNCHECKED_CAST")
        return ValuesResponse(request.dataModel, emptyList()) as RP
    }
}

private class LargeAddResponseStore(
    private val delegate: IsDataStore,
    private val executions: AtomicInteger,
) : IsDataStore by delegate {
    private val generatedValue = "x".repeat(12 * 1024 * 1024)

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ,
    ): RP {
        @Suppress("UNCHECKED_CAST")
        val response = delegate.execute(request) as AddResponse<DM>
        executions.incrementAndGet()
        val status = response.statuses.single() as AddSuccess<DM>
        // Model a large server-generated change without inflating the request body.
        @Suppress("UNCHECKED_CAST")
        return response.copy(statuses = listOf(status.copy(
            changes = listOf(Change(SimpleMarykModel.ref { value } with generatedValue)),
        ))) as RP
    }
}

private class MismatchedSecondResponseStore(
    private val delegate: IsDataStore,
) : IsDataStore by delegate {
    private var executions = 0

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ,
    ): RP {
        executions++
        if (executions == 1) return delegate.execute(request)
        @Suppress("UNCHECKED_CAST")
        return AddResponse(request.dataModel, emptyList()) as RP
    }
}

private suspend fun withServer(
    config: RemoteStoreServerConfig = RemoteStoreServerConfig(),
    limits: RemoteStoreServerLimits = RemoteStoreServerLimits(),
    dataModelsById: Map<UInt, IsRootDataModel> = mapOf(1u to SimpleMarykModel),
    dataStore: IsDataStore? = null,
    block: suspend (String, HttpClient) -> Unit,
) {
    val store = dataStore ?: InMemoryDataStore.open(dataModelsById = dataModelsById)
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

private fun addThenGetPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            listOf(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "a" }),
                SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16))),
            )
        ),
        testRequestContext(),
    )

private fun tooManyStoreRequestsPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            List(257) {
                SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))
            }
        ),
        testRequestContext(),
    )

private fun addThenDeniedScanPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-prefix" }),
            CompleteMarykModel.scan(allowTableScan = true),
        ),
        RequestContext(
            DefinitionsContext(
                dataModels = mutableMapOf(
                    SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel),
                    CompleteMarykModel.Meta.name to DataModelReference(CompleteMarykModel),
                )
            ),
            dataModel = SimpleMarykModel,
        ),
    )

private fun addThenOversizedFilterPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-prefix" }),
            SimpleMarykModel.scan(
                where = And(List(1_024) { Exists(SimpleMarykModel.ref { value }) }),
                allowTableScan = true,
            ),
        ),
        testRequestContext(),
    )

private fun filterWorkPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            SimpleMarykModel.scan(
                where = And(List(1_024) { Exists(SimpleMarykModel.ref { value }) }),
                allowTableScan = true,
            )
        ),
        testRequestContext(),
    )

private fun aggregationWorkPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            SimpleMarykModel.get(
                SimpleMarykModel.key(ByteArray(16)),
                aggregations = Aggregations(
                    *(0 until 129).map { index ->
                        "aggregation-$index" to ValueCount(SimpleMarykModel.ref { value })
                    }.toTypedArray()
                ),
            )
        ),
        testRequestContext(),
    )

private fun terminalReferenceFilterPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            NamedIndexSourceModel.scan(
                where = Equals(
                    NamedIndexSourceModel.ref { target } with NamedIndexTargetModel.key(ByteArray(16))
                ),
                allowTableScan = true,
            )
        ),
        authorizationRequestContext(),
    )

private fun terminalReferenceFilterFlowPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            NamedIndexSourceModel.scanUpdates(
                where = Equals(
                    NamedIndexSourceModel.ref { target } with NamedIndexTargetModel.key(ByteArray(16))
                ),
            )
        ),
        authorizationRequestContext(),
    )

private fun crossModelNamedIndexPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            NamedIndexSourceModel.scan(
                where = Matches("related-value" with "classified"),
                allowTableScan = true,
            )
        ),
        namedIndexRequestContext(),
    )

private fun crossModelNamedIndexFlowPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            NamedIndexSourceModel.scanUpdates(
                where = Matches("related-value" with "classified"),
            )
        ),
        namedIndexRequestContext(),
    )

private fun unknownNamedIndexPayload(): ByteArray =
    RemoteStoreCodec.encode(
        Requests.Serializer,
        Requests(
            SimpleMarykModel.scan(
                where = Matches("missing-index" with "classified"),
                allowTableScan = true,
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

private fun validProcessUpdatePayload(): ByteArray = processUpdatePayload(1uL)

private fun processUpdatePayload(version: ULong): ByteArray =
    RemoteStoreCodec.encode(
        UpdateResponse.Serializer,
        UpdateResponse(
            dataModel = SimpleMarykModel,
            update = OrderedKeysUpdate(
                keys = listOf(SimpleMarykModel.key(ByteArray(16))),
                version = version,
            ),
        ),
        testRequestContext(),
    )

private fun rejectedProcessUpdateKey() = SimpleMarykModel.key(ByteArray(16) { 1 })

private fun unsafeAdditionProcessUpdatePayload(): ByteArray =
    RemoteStoreCodec.encode(
        UpdateResponse.Serializer,
        UpdateResponse(
            dataModel = SimpleMarykModel,
            update = AdditionUpdate(
                key = rejectedProcessUpdateKey(),
                version = ULong.MAX_VALUE - 1uL,
                firstVersion = ULong.MAX_VALUE - 1uL,
                insertionIndex = 0,
                isDeleted = false,
                values = SimpleMarykModel.create { value with "haha-unsafe" },
            ),
        ),
        testRequestContext(),
    )

private fun initialChangesWithCreatePayload(nestedVersion: ULong = 1uL): ByteArray =
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
                                version = nestedVersion,
                                changes = listOf(ObjectCreate),
                            )
                        ),
                    )
                ),
            ),
        ),
        testRequestContext(),
    )

private fun changeWithSoftDeletePayload(): ByteArray =
    RemoteStoreCodec.encode(
        UpdateResponse.Serializer,
        UpdateResponse(
            dataModel = SimpleMarykModel,
            update = ChangeUpdate(
                key = SimpleMarykModel.key(ByteArray(16)),
                version = 1uL,
                index = 0,
                changes = listOf(
                    Change(SimpleMarykModel.ref { value } with "changed"),
                    ObjectSoftDeleteChange(true),
                ),
            ),
        ),
        testRequestContext(),
    )

private fun softDeleteChangePayload(): ByteArray =
    RemoteStoreCodec.encode(
        UpdateResponse.Serializer,
        UpdateResponse(
            dataModel = SimpleMarykModel,
            update = ChangeUpdate(
                key = SimpleMarykModel.key(ByteArray(16)),
                version = 1uL,
                index = 0,
                changes = listOf(ObjectSoftDeleteChange(true)),
            ),
        ),
        testRequestContext(),
    )

private fun restoreChangePayload(): ByteArray =
    RemoteStoreCodec.encode(
        UpdateResponse.Serializer,
        UpdateResponse(
            dataModel = SimpleMarykModel,
            update = ChangeUpdate(
                key = SimpleMarykModel.key(ByteArray(16)),
                version = 1uL,
                index = 0,
                changes = listOf(ObjectSoftDeleteChange(false)),
            ),
        ),
        testRequestContext(),
    )

private fun initialChangesWithMixedOperationsPayload(): ByteArray =
    RemoteStoreCodec.encode(
        UpdateResponse.Serializer,
        UpdateResponse(
            dataModel = SimpleMarykModel,
            update = InitialChangesUpdate(
                version = 3uL,
                changes = listOf(
                    DataObjectVersionedChange(
                        key = SimpleMarykModel.key(ByteArray(16)),
                        changes = listOf(
                            VersionedChanges(1uL, listOf(ObjectCreate)),
                            VersionedChanges(
                                2uL,
                                listOf(Change(SimpleMarykModel.ref { value } with "changed")),
                            ),
                            VersionedChanges(3uL, listOf(ObjectSoftDeleteChange(true))),
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

private fun authorizationRequestContext(): RequestContext =
    RequestContext(
        DefinitionsContext(
            dataModels = mutableMapOf(
                NamedIndexSourceModel.Meta.name to DataModelReference(NamedIndexSourceModel),
                NamedIndexTargetModel.Meta.name to DataModelReference(NamedIndexTargetModel),
            )
        ),
        dataModel = NamedIndexSourceModel,
    )

private fun namedIndexRequestContext(): RequestContext =
    RequestContext(
        DefinitionsContext(
            dataModels = mutableMapOf(
                NamedIndexSourceModel.Meta.name to DataModelReference(NamedIndexSourceModel),
                NamedIndexTargetModel.Meta.name to DataModelReference(NamedIndexTargetModel),
            )
        ),
        dataModel = NamedIndexSourceModel,
    )

private object NamedIndexTargetModel : RootDataModel<NamedIndexTargetModel>() {
    val value by string(index = 1u)
}

private object NamedIndexSourceModel : RootDataModel<NamedIndexSourceModel>(
    indexes = {
        NamedIndexSourceModel.run {
            listOf(
                AnyOf(
                    "related-value",
                    NamedIndexSourceModel.ref { target { value } },
                )
            )
        }
    }
) {
    val target by reference(
        index = 1u,
        dataModel = { NamedIndexTargetModel },
    )
}

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
    }.execute { response ->
        response.status.also { response.call.cancel() }
    }

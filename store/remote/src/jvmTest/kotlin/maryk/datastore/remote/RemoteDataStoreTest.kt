package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
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
import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import maryk.core.inject.Inject
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.invoke
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
import maryk.core.query.requests.CollectRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.RequestType
import maryk.core.query.requests.Requests
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.properties.types.Key
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AuthFail
import maryk.datastore.memory.InMemoryDataStore
import maryk.test.models.ReferencesModel
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import kotlin.time.Duration.Companion.milliseconds

class RemoteDataStoreTest {
    @Test
    fun canceledCloseStillClosesSshTunnel() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        val tunnelClosed = AtomicBoolean(false)
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://remote.example:8210",
                ssh = RemoteSshConfig(host = "ssh.example"),
                sshTunnelFactory = { _, _ ->
                    object : SshTunnel {
                        override val localPort = port

                        override fun close() {
                            tunnelClosed.set(true)
                        }
                    }
                },
            )
        )

        try {
            launch {
                currentCoroutineContext()[Job]?.cancel()
                remote.close()
            }.join()

            assertTrue(tunnelClosed.get())
        } finally {
            remote.close()
            server.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun preservesLegacyRemoteStoreConfigConstructorAndConnectEntryPoint() {
        val legacyConstructor = RemoteStoreConfig::class.java.constructors.singleOrNull { constructor ->
            constructor.parameterTypes.contentEquals(
                arrayOf(
                    String::class.java,
                    RemoteSshConfig::class.java,
                    SshTunnelFactory::class.java,
                    HttpClient::class.java,
                    String::class.java,
                    RemoteFlowRetryPolicy::class.java,
                )
            )
        }

        assertNotNull(legacyConstructor)
        assertTrue(
            RemoteDataStore.Companion::class.java.declaredMethods.any { method ->
                method.name == "connect" &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes.first() == RemoteStoreConfig::class.java
            }
        )
    }

    @Test
    fun connectRejectsBearerTokenOverPublicHttpBeforeNetworkIo() = runBoundedIntegrationTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    error("Bearer transport validation must run before network I/O")
                }
            }
        }

        try {
            val exception = assertFailsWith<IllegalArgumentException> {
                RemoteDataStore.connect(
                    RemoteStoreConfig(
                        baseUrl = "http://store.example.test:8210",
                        bearerToken = "secret",
                        httpClient = client,
                    )
                )
            }

            assertTrue(exception.message.orEmpty().contains("plaintext"))
        } finally {
            client.close()
        }
    }

    @Test
    fun connectAllowsPublicHttpBearerWithExplicitInsecureOptIn() = runBoundedIntegrationTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    error("Explicit insecure bearer transport opt-in reached the HTTP client")
                }
            }
        }

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(
                    RemoteStoreConfig(
                        baseUrl = "http://store.example.test:8210",
                        bearerToken = "secret",
                        httpClient = client,
                    ),
                    allowInsecureBearerTransport = true,
                )
            }

            assertEquals("Explicit insecure bearer transport opt-in reached the HTTP client", exception.message)
        } finally {
            client.close()
        }
    }

    @Test
    fun connectAllowsBearerTransportForHttpsAndLoopbackHttpUrls() = runBoundedIntegrationTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    error("Allowed bearer transport reached the HTTP client")
                }
            }
        }

        try {
            listOf(
                "https://store.example.test:8210",
                "http://localhost:8210",
                "http://127.0.0.1:8210",
                "http://[::1]:8210",
                "http://[0:0:0:0:0:0:0:1]:8210",
            ).forEach { baseUrl ->
                val exception = assertFailsWith<IllegalStateException>(baseUrl) {
                    RemoteDataStore.connect(
                        RemoteStoreConfig(
                            baseUrl = baseUrl,
                            bearerToken = "secret",
                            httpClient = client,
                        )
                    )
                }

                assertEquals("Allowed bearer transport reached the HTTP client", exception.message, baseUrl)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun retryPolicyRecognizesTransportFailuresOnly() {
        assertTrue(isRetryableRemoteFlowFailure(IOException("network down")))
        assertTrue(isRetryableRemoteFlowFailure(RemoteFlowDisconnectedException("cut")))
        assertEquals(false, isRetryableRemoteFlowFailure(IllegalStateException("invalid protocol")))
    }

    @Test
    fun capturesAuthoritativeRemoteSnapshotVersion() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        val port = ServerSocket(0).use { it.localPort }
        val engine = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port")
        )

        try {
            val add = remote.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-snapshot" })
            )
            val addVersion = assertIs<AddSuccess<*>>(add.statuses.single()).version

            assertTrue(remote.captureSnapshotVersion() > addVersion)
        } finally {
            remote.close()
            engine.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun rejectsConflictingLocalModelInstanceWithTheSameRemoteName() = runBoundedIntegrationTest {
        val executeCalls = AtomicInteger()
        val port = ServerSocket(0).use { it.localPort }
        val engine = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            rejectingExecuteModule(executeCalls)
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            assertEquals(SimpleMarykModel.Meta.name, FirstConflictingModel.SimpleMarykModel.Meta.name)
            assertFailsWith<IllegalStateException> {
                remote.execute(FirstConflictingModel.SimpleMarykModel.get())
            }

            assertFailsWith<IllegalArgumentException> {
                remote.execute(SecondConflictingModel.SimpleMarykModel.get())
            }
            assertEquals(1, executeCalls.get())
        } finally {
            remote.close()
            engine.stop(500, 500)
        }
    }

    @Test
    fun decodesRemoteResponseUsingCompatibleLocalModelInstance() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val port = ServerSocket(0).use { it.localPort }
        val engine = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val add = assertIs<AddSuccess<SimpleMarykModel>>(
                remote.execute(
                    SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-local-model" })
                ).statuses.single()
            )
            val response: ValuesResponse<SimpleMarykModel> = remote.execute(
                SimpleMarykModel.get(add.key)
            )

            assertEquals(SimpleMarykModel, response.dataModel)
            assertEquals("ha-local-model", response.values.single().values { value })
        } finally {
            remote.close()
            engine.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun remoteModelThenLocalModelDecodesWithTheLocalDefinition() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-remote-first" }))
        val port = ServerSocket(0).use { it.localPort }
        val engine = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val decodedRemoteModel = remote.dataModelsById.getValue(1u)
            remote.execute(decodedRemoteModel.scan(allowTableScan = true))

            val localResponse: ValuesResponse<SimpleMarykModel> = remote.execute(
                SimpleMarykModel.scan(allowTableScan = true)
            )

            assertEquals(SimpleMarykModel, localResponse.dataModel)
        } finally {
            remote.close()
            engine.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun localModelThenRemoteModelKeepsTheDecodedRemoteDefinition() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-local-first" }))
        val port = ServerSocket(0).use { it.localPort }
        val engine = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            remote.execute(SimpleMarykModel.scan(allowTableScan = true))
            val decodedRemoteModel = remote.dataModelsById.getValue(1u)
            val remoteResponse = remote.execute(decodedRemoteModel.scan(allowTableScan = true))

            assertEquals(decodedRemoteModel, remoteResponse.dataModel)
        } finally {
            remote.close()
            engine.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun concurrentSameNameRequestsAllowOnlyOneLocalModelBeforeTransport() = runBoundedIntegrationTest {
        val executeCalls = AtomicInteger()
        val port = ServerSocket(0).use { it.localPort }
        val engine = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            rejectingExecuteModule(executeCalls)
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
        val start = CompletableDeferred<Unit>()

        try {
            val results = coroutineScope {
                listOf(
                    async {
                        start.await()
                        runCatching { remote.execute(FirstConflictingModel.SimpleMarykModel.get()) }
                    },
                    async {
                        start.await()
                        runCatching { remote.execute(SecondConflictingModel.SimpleMarykModel.get()) }
                    },
                ).also { start.complete(Unit) }.map { it.await() }
            }

            assertEquals(1, results.count { it.exceptionOrNull() is IllegalArgumentException })
            assertEquals(1, executeCalls.get())
        } finally {
            remote.close()
            engine.stop(500, 500)
        }
    }

    @Test
    fun serverAuthorizerScopesByPrincipalModelAndAction() = runBoundedIntegrationTest {
        val decisions = mutableListOf<RemoteStoreAuthorizationRequest>()
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val port = ServerSocket(0).use { it.localPort }
        val engine = RemoteStoreServer(store).start(
            "127.0.0.1",
            port,
            wait = false,
            config = RemoteStoreServerConfig(
                bearerToken = "scoped-secret",
                authorizer = RemoteStoreAuthorizer { request ->
                    decisions.add(request)
                    request.requestType != RequestType.Add
                },
            ),
        )
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://127.0.0.1:$port",
                bearerToken = "scoped-secret",
            )
        )

        try {
            val response = remote.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "denied" })
            )
            assertIs<AuthFail<*>>(response.statuses.single())
            val decision = decisions.single { it.requestType == RequestType.Add }
            assertEquals("bearer", decision.principal.id)
            assertEquals(SimpleMarykModel.Meta.name, decision.modelName)
            assertEquals(RemoteStoreOperation.Execute, decision.operation)
        } finally {
            remote.close()
            engine.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun executeFlowReconnectsWithAtLeastOnceInitialState() = runBoundedIntegrationTest {
        val connections = AtomicInteger()
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            reconnectingFlowModule(connections)
        }.start(wait = false)
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://127.0.0.1:$port",
                flowRetryPolicy = RemoteFlowRetryPolicy(
                    maxReconnectAttempts = 2u,
                    initialDelayMillis = 0,
                    maxDelayMillis = 0,
                ),
            )
        )

        try {
            val updates = withTimeout(2_000.milliseconds) {
                remote.executeFlow(
                    SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))
                ).take(3).toList()
            }

            assertEquals(listOf(1uL, 2uL, 1uL), updates.map { it.version })
            assertEquals(2, connections.get())
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowResetsRetryBudgetAfterDeliveringNewUpdates() = runBoundedIntegrationTest {
        val connections = AtomicInteger()
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            progressOnEachConnectionFlowModule(connections, keepConnectionOpenAt = 3)
        }.start(wait = false)
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://127.0.0.1:$port",
                flowRetryPolicy = RemoteFlowRetryPolicy(
                    maxReconnectAttempts = 1u,
                    initialDelayMillis = 0,
                    maxDelayMillis = 0,
                ),
            )
        )

        try {
            val updates = withTimeout(2_000.milliseconds) {
                remote.executeFlow(
                    SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))
                ).take(3).toList()
            }

            assertEquals(listOf(1uL, 2uL, 3uL), updates.map { it.version })
            assertEquals(3, connections.get())
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun closeAllListenersStopsAReconnectableRemoteFlow() = runBoundedIntegrationTest {
        val connections = AtomicInteger()
        val firstConnectionOpened = CompletableDeferred<Unit>()
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            indefinitelyOpenFlowModule(connections, firstConnectionOpened)
        }.start(wait = false)
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://127.0.0.1:$port",
                flowRetryPolicy = RemoteFlowRetryPolicy(
                    maxReconnectAttempts = 2u,
                    initialDelayMillis = 0,
                    maxDelayMillis = 0,
                ),
            )
        )
        val collector = async {
            runCatching {
                remote.executeFlow(
                    SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))
                ).collect()
            }
        }

        try {
            withTimeout(2_000.milliseconds) { firstConnectionOpened.await() }
            remote.closeAllListeners()
            withTimeout(2_000.milliseconds) { collector.await() }
            assertEquals(1, connections.get())
        } finally {
            remote.close()
            collector.cancel()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowDeliversAtLeastOnceUpdatesAtReconnectBoundary() = runBoundedIntegrationTest {
        val connections = AtomicInteger()
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            sameVersionReconnectFlowModule(connections)
        }.start(wait = false)
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://127.0.0.1:$port",
                flowRetryPolicy = RemoteFlowRetryPolicy(
                    maxReconnectAttempts = 2u,
                    initialDelayMillis = 0,
                    maxDelayMillis = 0,
                ),
            )
        )

        try {
            val updates = withTimeout(2_000.milliseconds) {
                remote.executeFlow(
                    SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))
                ).take(3).toList()
            }.map { it as OrderedKeysUpdate<SimpleMarykModel> }

            assertEquals(listOf(1uL, 1uL, 1uL), updates.map { it.version })
            assertEquals(listOf(1, 1, 2), updates.map { it.keys.single().bytes.last().toInt() })
            assertEquals(2, connections.get())
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowDeliversLowerHlcUpdateAfterReconnect() = runBoundedIntegrationTest {
        val connections = AtomicInteger()
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            lowerHlcReconnectFlowModule(connections, keepConnectionOpenAt = 2)
        }.start(wait = false)
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://127.0.0.1:$port",
                flowRetryPolicy = RemoteFlowRetryPolicy(
                    maxReconnectAttempts = 2u,
                    initialDelayMillis = 0,
                    maxDelayMillis = 0,
                ),
            )
        )

        try {
            val updates = withTimeout(2_000.milliseconds) {
                remote.executeFlow(
                    SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))
                ).take(3).toList()
            }.map { it as OrderedKeysUpdate<SimpleMarykModel> }

            assertEquals(listOf(40uL, 40uL, 5uL), updates.map { it.version })
            assertEquals(listOf(1, 1, 2), updates.map { it.keys.single().bytes.last().toInt() })
            assertEquals(2, connections.get())
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun retryPolicyValidatesBounds() {
        assertFailsWith<IllegalArgumentException> {
            RemoteFlowRetryPolicy(initialDelayMillis = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteFlowRetryPolicy(initialDelayMillis = 2, maxDelayMillis = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteFlowRetryPolicy(backoffMultiplier = 0.5)
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteFlowRetryPolicy(heartbeatTimeoutMillis = 0)
        }
    }

    @Test
    fun executeFlowProtocolV2AcceptsHeartbeatFrames() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            heartbeatThenUpdateFlowModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://127.0.0.1:$port",
                flowRetryPolicy = RemoteFlowRetryPolicy(heartbeatTimeoutMillis = 1_000),
            )
        )

        try {
            val update = withTimeout(2_000.milliseconds) {
                remote.executeFlow(
                    SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))
                ).first()
            }
            assertEquals(3uL, update.version)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowRetriesWhenAcceptedConnectionNeverSendsResponseHeaders() = runBoundedIntegrationTest {
        val connections = AtomicInteger()
        val firstConnectionAccepted = CompletableDeferred<Unit>()
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            stalledHeaderThenUpdateFlowModule(connections, firstConnectionAccepted)
        }.start(wait = false)
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://127.0.0.1:$port",
                flowRetryPolicy = RemoteFlowRetryPolicy(
                    maxReconnectAttempts = 1u,
                    initialDelayMillis = 0,
                    maxDelayMillis = 0,
                    heartbeatTimeoutMillis = 100,
                ),
            )
        )

        try {
            val update = withTimeout(2_000.milliseconds) {
                remote.executeFlow(
                    SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))
                ).first()
            }

            firstConnectionAccepted.await()
            assertEquals(1uL, update.version)
            assertEquals(2, connections.get())
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowRetriesWhenFramePayloadStallsAfterItsLengthPrefix() = runBoundedIntegrationTest {
        val connections = AtomicInteger()
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            stalledPayloadThenUpdateFlowModule(connections)
        }.start(wait = false)
        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://127.0.0.1:$port",
                flowRetryPolicy = RemoteFlowRetryPolicy(
                    maxReconnectAttempts = 1u,
                    initialDelayMillis = 0,
                    maxDelayMillis = 0,
                    heartbeatTimeoutMillis = 100,
                ),
            )
        )

        try {
            val update = withTimeout(2_000.milliseconds) {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }

            assertEquals(1uL, update.version)
            assertEquals(2, connections.get())
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsInsteadOfSilentlyDroppingWhenCollectorBackpressures() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            backpressuredFlowModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<RemoteFlowBackpressureException> {
                withTimeout(5_000.milliseconds) {
                    remote.executeFlow(
                        SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))
                    ).buffer(0).collect {
                        delay(50)
                    }
                }
            }
            assertTrue(exception.message?.contains("backpressure") == true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun remoteExecuteAndFlow() = runBoundedIntegrationTest {
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
        val engine = RemoteStoreServer(store).start(
            "127.0.0.1",
            port,
            wait = false,
            config = RemoteStoreServerConfig(bearerToken = "integration-secret"),
        )

        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(
                baseUrl = "http://127.0.0.1:$port",
                bearerToken = "integration-secret",
            )
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

            val replicatedValues = SimpleMarykModel.create {
                value with "replicated"
            }
            val processResponse: ProcessResponse<SimpleMarykModel> = remote.processUpdate(
                UpdateResponse(
                    dataModel = SimpleMarykModel,
                    update = AdditionUpdate(
                        key = SimpleMarykModel.key(ByteArray(16) { 1 }),
                        version = 1uL,
                        firstVersion = 1uL,
                        insertionIndex = 0,
                        isDeleted = false,
                        values = replicatedValues,
                    ),
                )
            )
            assertIs<AddResponse<*>>(processResponse.result)
        } finally {
            remote.close()
            engine.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun remoteExecuteBatchReturnsEveryResponseInOrder() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val port = ServerSocket(0).use { it.localPort }
        val engine = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val responses = remote.execute(
                Requests(
                    SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-first" }),
                    SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-second" }),
                )
            )

            assertEquals(2, responses.size)
            assertIs<AddResponse<*>>(responses[0])
            assertIs<AddResponse<*>>(responses[1])
        } finally {
            remote.close()
            engine.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun remoteExecuteBatchCollectsAndInjectsResponseValues() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(
            dataModelsById = mapOf(
                1u to SimpleMarykModel,
                2u to ReferencesModel,
            )
        )
        val port = ServerSocket(0).use { it.localPort }
        val engine = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val first = assertIs<AddSuccess<SimpleMarykModel>>(
                remote.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-first" }))
                    .statuses.single()
            )
            val second = assertIs<AddSuccess<SimpleMarykModel>>(
                remote.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-second" }))
                    .statuses.single()
            )
            val references = ReferencesModel.create {
                this.references with listOf(first.key, second.key)
            }
            val referencesKey = assertIs<AddSuccess<ReferencesModel>>(
                remote.execute(ReferencesModel.add(references)).statuses.single()
            ).key

            val context = RequestContext(
                DefinitionsContext(
                    mutableMapOf(
                        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel),
                        ReferencesModel.Meta.name to DataModelReference(ReferencesModel),
                    )
                )
            )
            val injectedGet = GetRequest.create(context = context) {
                from with SimpleMarykModel
                keys with Inject(
                    "referencedKeys",
                    ValuesResponse {
                        values.atAny {
                            values.refWithDM(ReferencesModel) { this.references }
                        }
                    },
                )
            }

            val requests = Requests.create(context = context) {
                this.requests -= listOf(
                    RequestType.Collect(
                        CollectRequest("referencedKeys", ReferencesModel.get(referencesKey))
                    ),
                    RequestType.Get(injectedGet),
                )
            }
            val responses = remote.execute(requests)

            assertEquals(2, responses.size)
            assertEquals(
                setOf("ha-first", "ha-second"),
                assertIs<ValuesResponse<SimpleMarykModel>>(responses[1])
                    .values
                    .map { it.values { value } }
                    .toSet(),
            )
        } finally {
            remote.close()
            engine.stop(500, 500)
            store.close()
        }
    }

    @Test
    fun connectRejectsBlankBearerToken() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    bearerToken = " ",
                )
            )
        }

        assertTrue(exception.message.orEmpty().contains("bearer token cannot be blank"))
    }

    @Test
    fun executeFailsOnTrailingBytes() = runBoundedIntegrationTest {
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
            assertEquals(exception.message?.contains("trailing bytes"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun connectRejectsUnsupportedUrlScheme() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "ftp://127.0.0.1:8210"))
        }
        assertEquals(exception.message?.contains("only supports http or https URLs"), true)
    }

    @Test
    fun connectRejectsBaseUrlWithQuery() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:8210?x=1"))
        }
        assertEquals(exception.message?.contains("query parameters"), true)
    }

    @Test
    fun connectRejectsBaseUrlWithFragment() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:8210#anchor"))
        }
        assertEquals(exception.message?.contains("fragment"), true)
    }

    @Test
    fun connectRejectsBaseUrlWithUserInfo() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://user:pass@127.0.0.1:8210"))
        }
        assertEquals(exception.message?.contains("user info"), true)
    }

    @Test
    fun connectRejectsBaseUrlWithInvalidPort() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:70000"))
        }
        assertEquals(exception.message?.contains("70000"), true)
    }

    @Test
    fun connectRejectsMalformedBaseUrl() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:abc"))
        }
        assertEquals(exception.message?.contains("invalid"), true)
    }

    @Test
    fun connectRejectsBlankBaseUrl() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "   "))
        }
        val message = exception.message.orEmpty()
        assertTrue(message.contains("blank") || message.contains("whitespace"))
    }

    @Test
    fun connectRejectsBaseUrlWithLeadingWhitespace() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(RemoteStoreConfig(baseUrl = " http://127.0.0.1:8210"))
        }
        assertEquals(exception.message?.contains("leading or trailing whitespace"), true)
    }

    @Test
    fun connectRejectsBlankSshHost() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = ""),
                )
            )
        }
        assertEquals(exception.message?.contains("SSH host cannot be blank"), true)
    }

    @Test
    fun connectRejectsOutOfRangeSshPort() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", port = 70000),
                )
            )
        }
        assertEquals(exception.message?.contains("SSH port must be between"), true)
    }

    @Test
    fun connectRejectsOutOfRangeSshLocalPort() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", localPort = 70000),
                )
            )
        }
        assertEquals(exception.message?.contains("local port"), true)
    }

    @Test
    fun sshTunnelRejectsOccupiedLocalPort() {
        ServerSocket().use { server ->
            server.bind(InetSocketAddress("127.0.0.1", 0))

            val exception = assertFailsWith<IllegalStateException> {
                defaultSshTunnelFactory()!!.open(
                    RemoteSshConfig(host = "localhost", localPort = server.localPort),
                    SshTarget(host = "127.0.0.1", port = 1)
                )
            }

            assertEquals(exception.message?.contains("already in use"), true)
        }
    }

    @Test
    fun connectRejectsOutOfRangeSshRemotePort() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", remotePort = 70000),
                )
            )
        }
        assertEquals(exception.message?.contains("remote port"), true)
    }

    @Test
    fun connectRejectsBlankSshUser() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", user = " "),
                )
            )
        }
        assertEquals(exception.message?.contains("SSH user cannot be blank"), true)
    }

    @Test
    fun connectRejectsBlankSshRemoteHost() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", remoteHost = " "),
                )
            )
        }
        assertEquals(exception.message?.contains("remote host cannot be blank"), true)
    }

    @Test
    fun connectRejectsBlankSshIdentityFile() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", identityFile = " "),
                )
            )
        }
        assertEquals(exception.message?.contains("identity file cannot be blank"), true)
    }

    @Test
    fun connectRejectsBlankSshExtraArgs() = runBoundedIntegrationTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            RemoteDataStore.connect(
                RemoteStoreConfig(
                    baseUrl = "http://127.0.0.1:8210",
                    ssh = RemoteSshConfig(host = "host", extraArgs = listOf("-N", " ")),
                )
            )
        }
        assertEquals(exception.message?.contains("extra arguments"), true)
    }

    @Test
    fun connectRejectsDuplicateModelIds() = runBoundedIntegrationTest {
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
            assertEquals(exception.message?.contains("Duplicate model id"), true)
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun connectClosesSshTunnelWhenInfoValidationFails() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            duplicateInfoModule(
                modelIds = listOf(
                    RemoteStoreModelId(1u, SimpleMarykModel.Meta.name),
                    RemoteStoreModelId(1u, SimpleMarykModel.Meta.name),
                ),
            )
        }.start(wait = false)
        val tunnelClosed = AtomicBoolean(false)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(
                    RemoteStoreConfig(
                        baseUrl = "http://remote.example:1234",
                        bearerToken = "secret",
                        ssh = RemoteSshConfig(host = "ssh.example"),
                        sshTunnelFactory = { _, _ ->
                            object : SshTunnel {
                                override val localPort = port

                                override fun close() {
                                    tunnelClosed.set(true)
                                }
                            }
                        },
                    )
                )
            }

            assertEquals(exception.message?.contains("Duplicate model id"), true)
            assertTrue(tunnelClosed.get())
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun connectKeepsOriginalFailureWhenSshTunnelCloseFails() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            duplicateInfoModule(
                modelIds = listOf(
                    RemoteStoreModelId(1u, SimpleMarykModel.Meta.name),
                    RemoteStoreModelId(1u, SimpleMarykModel.Meta.name),
                ),
            )
        }.start(wait = false)
        val tunnelClosed = AtomicBoolean(false)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(
                    RemoteStoreConfig(
                        baseUrl = "http://remote.example:1234",
                        ssh = RemoteSshConfig(host = "ssh.example"),
                        sshTunnelFactory = { _, _ ->
                            object : SshTunnel {
                                override val localPort = port

                                override fun close() {
                                    tunnelClosed.set(true)
                                    throw IllegalStateException("close failed")
                                }
                            }
                        },
                    )
                )
            }

            assertEquals(exception.message?.contains("Duplicate model id"), true)
            assertTrue(tunnelClosed.get())
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun connectRejectsDuplicateModelNames() = runBoundedIntegrationTest {
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
            assertEquals(exception.message?.contains("Duplicate model name"), true)
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun connectRejectsUnexpectedInfoContentType() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            wrongInfoContentTypeModule()
        }.start(wait = false)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            }
            assertEquals(exception.message?.contains("unexpected Content-Type"), true)
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun connectRejectsEmptyInfoPayload() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            emptyInfoPayloadModule()
        }.start(wait = false)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            }
            assertEquals(exception.message?.contains("empty payload"), true)
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun connectIncludesErrorBodyPreview() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            infoServerErrorModule()
        }.start(wait = false)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            }
            assertEquals(exception.message?.contains("boom-info"), true)
        } finally {
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFailsOnNegativeLengthPrefix() = runBoundedIntegrationTest {
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
            assertEquals(exception.message?.contains("Invalid response length prefix"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeAcceptsLegacyUnframedSingleResponse() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            legacyExecuteModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val response = remote.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "legacy" })
            )
            assertIs<AddSuccess<*>>(response.statuses.single())
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFailsOnZeroLengthPrefix() = runBoundedIntegrationTest {
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
            assertEquals(exception.message?.contains("Invalid response length prefix: 0"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFailsOnUnexpectedContentType() = runBoundedIntegrationTest {
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
            assertEquals(exception.message?.contains("unexpected Content-Type"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFailsOnEmptyPayload() = runBoundedIntegrationTest {
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
            assertEquals(exception.message?.contains("empty payload"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeRejectsInvalidResponseContentLength() = runBoundedIntegrationTest {
        val server = RawRemoteServer(
            infoBody = defaultInfoBytes(),
            secondResponseHeaders = listOf(
                "Content-Type: ${RemoteStoreProtocol.contentType}",
                "Content-Length: invalid",
            ),
            secondResponseBody = RemoteStoreCodec.lengthPrefix(1) + byteArrayOf(0x01),
        )

        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:${server.port}"))

        try {
            val values = SimpleMarykModel.create {
                value with "hello"
            }
            val exception = assertFailsWith<IllegalStateException> {
                remote.execute(SimpleMarykModel.add(values))
            }
            assertEquals(exception.message?.contains("invalid Content-Length"), true)
        } finally {
            remote.close()
            server.close()
        }
    }

    @Test
    fun nonFlowRequestTimesOutResponseConsumptionAfterHeaders(): Unit = runBoundedIntegrationTest {
        val headersReceived = CompletableDeferred<Unit>()

        assertFailsWith<TimeoutCancellationException> {
            executeNonFlowRequest(
                timeoutMillis = 25,
                request = {
                    headersReceived.complete(Unit)
                },
                consumeResponse = {
                    delay(250)
                },
            )
        }
        assertTrue(headersReceived.isCompleted)
    }

    @Test
    fun executeFailsOnOversizedLengthPrefix() = runBoundedIntegrationTest {
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
            assertEquals(exception.message?.contains("frame exceeds max size"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnNegativeLengthPrefix() = runBoundedIntegrationTest {
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
            assertEquals(exception.message?.contains("Invalid streamed response length prefix"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnUnexpectedContentType() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            wrongFlowContentTypeModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertEquals(exception.message?.contains("unexpected Content-Type"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnZeroLengthPrefix() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            zeroLengthFlowModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertEquals(exception.message?.contains("Invalid streamed response length prefix: 0"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnOversizedLengthPrefix() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            oversizedLengthFlowModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertEquals(exception.message?.contains("frame exceeds max size"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowEndsWithoutUpdates(): Unit = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            emptyFlowStreamModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            assertFailsWith<NoSuchElementException> {
                withTimeout(2_000.milliseconds) {
                    remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
                }
            }
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnEmptyUpdateFrame() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            emptyFlowUpdatesModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertEquals(exception.message?.contains("empty update frame"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnTruncatedLengthPrefix() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            truncatedLengthFlowModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertEquals(exception.message?.isNotBlank(), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnTruncatedPayload() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            truncatedPayloadFlowModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertEquals(exception.message?.isNotBlank(), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }

    @Test
    fun executeFlowFailsOnDataModelMismatch() = runBoundedIntegrationTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            mismatchedFlowDataModelModule()
        }.start(wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

        try {
            val exception = assertFailsWith<IllegalStateException> {
                remote.executeFlow(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16)))).first()
            }
            assertEquals(exception.message?.contains("data model mismatch"), true)
        } finally {
            remote.close()
            server.stop(500, 500)
        }
    }
}

private fun Application.reconnectingFlowModule(connections: AtomicInteger) {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            val connection = connections.incrementAndGet()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                val context = RequestContext(
                    definitionsContext = DefinitionsContext(
                        dataModels = mutableMapOf(
                            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
                        )
                    ),
                    dataModel = SimpleMarykModel,
                )
                val key = SimpleMarykModel.key(ByteArray(16))
                val updates = if (connection == 1) {
                    listOf(
                        OrderedKeysUpdate(listOf(key), 1uL),
                        OrderedKeysUpdate(listOf(key), 2uL),
                    )
                } else {
                    listOf(
                        OrderedKeysUpdate(listOf(key), 1uL),
                        OrderedKeysUpdate(listOf(key), 2uL),
                        OrderedKeysUpdate(listOf(key), 3uL),
                    )
                }
                for (update in updates) {
                    val payload = RemoteStoreCodec.encode(
                        UpdatesResponse.Serializer,
                        UpdatesResponse(SimpleMarykModel, listOf(update)),
                        context,
                    )
                    writeFully(RemoteStoreCodec.lengthPrefix(payload.size))
                    writeFully(payload)
                    flush()
                }
            }
        }
    }
}

private fun Application.progressOnEachConnectionFlowModule(
    connections: AtomicInteger,
    keepConnectionOpenAt: Int? = null,
) {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            val connection = connections.incrementAndGet()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                val context = RequestContext(
                    definitionsContext = DefinitionsContext(
                        dataModels = mutableMapOf(
                            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
                        )
                    ),
                    dataModel = SimpleMarykModel,
                )
                val payload = RemoteStoreCodec.encode(
                    UpdatesResponse.Serializer,
                    UpdatesResponse(
                        SimpleMarykModel,
                        listOf(OrderedKeysUpdate(listOf(SimpleMarykModel.key(ByteArray(16))), connection.toULong())),
                    ),
                    context,
                )
                writeFully(RemoteStoreCodec.lengthPrefix(payload.size))
                writeFully(payload)
                flush()
                if (connection == keepConnectionOpenAt) {
                    awaitCancellation()
                }
            }
        }
    }
}

private fun Application.indefinitelyOpenFlowModule(
    connections: AtomicInteger,
    firstConnectionOpened: CompletableDeferred<Unit>,
) {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            connections.incrementAndGet()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                firstConnectionOpened.complete(Unit)
                awaitCancellation()
            }
        }
    }
}

private fun Application.stalledHeaderThenUpdateFlowModule(
    connections: AtomicInteger,
    firstConnectionAccepted: CompletableDeferred<Unit>,
) {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            if (connections.incrementAndGet() == 1) {
                firstConnectionAccepted.complete(Unit)
                awaitCancellation()
            }
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                val context = RequestContext(
                    definitionsContext = DefinitionsContext(
                        dataModels = mutableMapOf(
                            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
                        )
                    ),
                    dataModel = SimpleMarykModel,
                )
                val payload = RemoteStoreCodec.encode(
                    UpdatesResponse.Serializer,
                    UpdatesResponse(
                        SimpleMarykModel,
                        listOf(OrderedKeysUpdate(listOf(SimpleMarykModel.key(ByteArray(16))), 1uL)),
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

private fun Application.stalledPayloadThenUpdateFlowModule(connections: AtomicInteger) {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            if (connections.incrementAndGet() == 1) {
                call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                    writeFully(RemoteStoreCodec.lengthPrefix(1))
                    flush()
                    awaitCancellation()
                }
            } else {
                call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                    val context = RequestContext(
                        definitionsContext = DefinitionsContext(
                            dataModels = mutableMapOf(
                                SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
                            )
                        ),
                        dataModel = SimpleMarykModel,
                    )
                    val payload = RemoteStoreCodec.encode(
                        UpdatesResponse.Serializer,
                        UpdatesResponse(
                            SimpleMarykModel,
                            listOf(OrderedKeysUpdate(listOf(SimpleMarykModel.key(ByteArray(16))), 1uL)),
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
}

private fun Application.sameVersionReconnectFlowModule(connections: AtomicInteger) {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            val connection = connections.incrementAndGet()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                val context = RequestContext(
                    definitionsContext = DefinitionsContext(
                        dataModels = mutableMapOf(
                            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
                        )
                    ),
                    dataModel = SimpleMarykModel,
                )
                val updates = if (connection == 1) {
                    listOf(OrderedKeysUpdate(listOf(keyEndingIn(1)), 1uL))
                } else {
                    listOf(
                        OrderedKeysUpdate(listOf(keyEndingIn(1)), 1uL),
                        OrderedKeysUpdate(listOf(keyEndingIn(2)), 1uL),
                        OrderedKeysUpdate(listOf(keyEndingIn(3)), 2uL),
                    )
                }
                for (update in updates) {
                    val payload = RemoteStoreCodec.encode(
                        UpdatesResponse.Serializer,
                        UpdatesResponse(SimpleMarykModel, listOf(update)),
                        context,
                    )
                    writeFully(RemoteStoreCodec.lengthPrefix(payload.size))
                    writeFully(payload)
                    flush()
                }
            }
        }
    }
}

private fun Application.lowerHlcReconnectFlowModule(
    connections: AtomicInteger,
    keepConnectionOpenAt: Int? = null,
) {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            val connection = connections.incrementAndGet()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                val context = RequestContext(
                    definitionsContext = DefinitionsContext(
                        dataModels = mutableMapOf(
                            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
                        )
                    ),
                    dataModel = SimpleMarykModel,
                )
                val updates = if (connection == 1) {
                    listOf(OrderedKeysUpdate(listOf(keyEndingIn(1)), 40uL))
                } else {
                    listOf(
                        OrderedKeysUpdate(listOf(keyEndingIn(1)), 40uL),
                        OrderedKeysUpdate(listOf(keyEndingIn(2)), 5uL),
                    )
                }
                for (update in updates) {
                    val payload = RemoteStoreCodec.encode(
                        UpdatesResponse.Serializer,
                        UpdatesResponse(SimpleMarykModel, listOf(update)),
                        context,
                    )
                    writeFully(RemoteStoreCodec.lengthPrefix(payload.size))
                    writeFully(payload)
                    flush()
                }
                if (connection == keepConnectionOpenAt) {
                    awaitCancellation()
                }
            }
        }
    }
}

private fun keyEndingIn(value: Byte): Key<SimpleMarykModel> =
    SimpleMarykModel.key(ByteArray(16).also { it[it.lastIndex] = value })

private fun Application.heartbeatThenUpdateFlowModule() {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.flowPath) {
            call.receiveChannel().readRemaining().readByteArray()
            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                writeFully(RemoteStoreCodec.lengthPrefix(0))
                val context = RequestContext(
                    definitionsContext = DefinitionsContext(
                        dataModels = mutableMapOf(
                            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
                        )
                    ),
                    dataModel = SimpleMarykModel,
                )
                val payload = RemoteStoreCodec.encode(
                    UpdatesResponse.Serializer,
                    UpdatesResponse(
                        SimpleMarykModel,
                        listOf(
                            OrderedKeysUpdate(
                                listOf(SimpleMarykModel.key(ByteArray(16))),
                                3uL,
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

private fun Application.backpressuredFlowModule() {
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
                        dataModels = mutableMapOf(
                            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
                        )
                    ),
                    dataModel = SimpleMarykModel,
                )
                repeat(128) { index ->
                    val payload = RemoteStoreCodec.encode(
                        UpdatesResponse.Serializer,
                        UpdatesResponse(
                            SimpleMarykModel,
                            listOf(OrderedKeysUpdate(listOf(keyEndingIn(index.toByte())), index.toULong() + 1uL)),
                        ),
                        context,
                    )
                    writeFully(RemoteStoreCodec.lengthPrefix(payload.size))
                    writeFully(payload)
                }
                flush()
            }
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
                statuses = listOf(AddSuccess(key = key, version = 1uL, changes = emptyList())),
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

private fun Application.legacyExecuteModule() {
    val infoBytes = defaultInfoBytes()

    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.executePath) {
            call.receiveChannel().readRemaining().readByteArray()
            val values = SimpleMarykModel.create { value with "legacy" }
            val response = AddResponse(
                dataModel = SimpleMarykModel,
                statuses = listOf(
                    AddSuccess(
                        key = SimpleMarykModel.key(values),
                        version = 1uL,
                        changes = emptyList(),
                    )
                ),
            )
            val context = RequestContext(
                definitionsContext = DefinitionsContext(
                    dataModels = mutableMapOf(
                        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
                    )
                ),
                dataModel = SimpleMarykModel,
            )
            call.respondBytes(
                RemoteStoreCodec.encode(AddResponse.Serializer, response, context),
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

private class RawRemoteServer(
    infoBody: ByteArray,
    secondResponseHeaders: List<String>,
    secondResponseBody: ByteArray,
) : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    val port: Int = serverSocket.localPort
    private val responses = listOf(
        RawResponse(
            headers = listOf(
                "Content-Type: ${RemoteStoreProtocol.contentType}",
                "Content-Length: ${infoBody.size}",
            ),
            body = infoBody,
        ),
        RawResponse(
            headers = secondResponseHeaders,
            body = secondResponseBody,
        ),
    )
    private val thread = Thread {
        runCatching {
            respondAll()
        }
    }.apply {
        isDaemon = true
        start()
    }

    private fun respondAll() {
        var responseIndex = 0
        while (responseIndex < responses.size) {
            serverSocket.accept().use { socket ->
                socket.soTimeout = 2_000
                val input = BufferedInputStream(socket.getInputStream())
                while (responseIndex < responses.size) {
                    val headers = readHeaders(input) ?: break
                    drainRequestBody(input, headers)
                    socket.writeResponse(
                        response = responses[responseIndex],
                        close = responseIndex == responses.lastIndex,
                    )
                    responseIndex++
                }
            }
        }
    }

    private fun readHeaders(input: BufferedInputStream): List<String>? {
        val requestLine = readHeaderLine(input) ?: return null
        if (requestLine.isEmpty()) return null
        val headers = mutableListOf<String>()
        while (true) {
            val line = readHeaderLine(input) ?: return null
            if (line.isEmpty()) return headers
            headers += line
        }
    }

    private fun readHeaderLine(input: BufferedInputStream): String? {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val value = input.read()
            if (value == -1) return null
            if (value == '\n'.code) {
                if (bytes.lastOrNull() == '\r'.code.toByte()) {
                    bytes.removeAt(bytes.lastIndex)
                }
                return bytes.toByteArray().decodeToString()
            }
            bytes += value.toByte()
        }
    }

    private fun drainRequestBody(input: BufferedInputStream, headers: List<String>) {
        val contentLength = headers.firstNotNullOfOrNull {
            val parts = it.split(':', limit = 2)
            parts.takeIf { partList ->
                partList.size == 2 && partList[0].equals("Content-Length", ignoreCase = true)
            }?.get(1)?.trim()?.toIntOrNull()
        } ?: return
        var remaining = contentLength
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong()).toInt()
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() == -1) {
                return
            } else {
                remaining--
            }
        }
    }

    private fun Socket.writeResponse(response: RawResponse, close: Boolean) {
        val headerBytes = buildString {
            append("HTTP/1.1 200 OK\r\n")
            response.headers.forEach { append("$it\r\n") }
            append("Connection: ${if (close) "close" else "keep-alive"}\r\n")
            append("\r\n")
        }.encodeToByteArray()
        getOutputStream().write(headerBytes)
        getOutputStream().write(response.body)
        getOutputStream().flush()
    }

    override fun close() {
        serverSocket.close()
        thread.join(1_000)
    }

    private data class RawResponse(
        val headers: List<String>,
        val body: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RawResponse) return false

            if (headers != other.headers) return false
            if (!body.contentEquals(other.body)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = headers.hashCode()
            result = 31 * result + body.contentHashCode()
            return result
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

private fun Application.rejectingExecuteModule(executeCalls: AtomicInteger) {
    val infoBytes = defaultInfoBytes()
    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondBytes(infoBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
        post(RemoteStoreProtocol.executePath) {
            call.receiveChannel().readRemaining().readByteArray()
            executeCalls.incrementAndGet()
            call.respondBytes(
                "rejected".encodeToByteArray(),
                ContentType.Text.Plain,
                HttpStatusCode.BadRequest,
            )
        }
    }
}

private object FirstConflictingModel {
    object SimpleMarykModel : RootDataModel<SimpleMarykModel>() {
        val value by string(index = 1u, default = "haha", regEx = "ha.*")
    }
}

private object SecondConflictingModel {
    object SimpleMarykModel : RootDataModel<SimpleMarykModel>() {
        val otherValue by string(index = 1u)
    }
}

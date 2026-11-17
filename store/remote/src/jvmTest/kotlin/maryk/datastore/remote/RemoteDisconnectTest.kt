package maryk.datastore.remote

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.StorageFormat
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.Requests
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.DataStoreStateBackupManifest
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.DataStoreStateBackupWriter
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.StateBackupDataStore
import maryk.test.models.SimpleMarykModel

class RemoteDisconnectTest {
    @Test
    fun abruptDisconnectCancelsIdleBackupAndReleasesAdmission() = runBoundedIntegrationTest<Unit> {
        val source = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val entered = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val served = object : IsDataStore by source, StateBackupDataStore by source {
            override suspend fun writeStateBackup(
                writer: DataStoreStateBackupWriter,
                options: DataStoreStateBackupOptions,
            ): DataStoreStateBackupManifest {
                try {
                    entered.complete(Unit)
                    awaitCancellation()
                } finally {
                    finished.complete(Unit)
                }
            }
        }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            remoteStoreModule(served, limits = RemoteStoreServerLimits(maxConcurrentCalls = 1))
        }.start(wait = false)
        val client = createDefaultHttpClient()
        var socket: Socket? = null
        try {
            val port = server.engine.resolvedConnectors().single().port
            socket = withContext(Dispatchers.IO) {
                Socket("127.0.0.1", port).also {
                    it.getOutputStream().write((
                        "GET ${RemoteStoreProtocol.stateBackupPath}?captureId=${Uuid.random()} HTTP/1.1\r\n" +
                            "Host: 127.0.0.1:$port\r\n" +
                            "${RemoteStoreProtocol.valueStorageFormatHeader}: ${StorageFormat.CURRENT}\r\n\r\n"
                        ).encodeToByteArray())
                    it.getOutputStream().flush()
                }
            }
            withTimeout(5_000) { entered.await() }
            assertEquals(HttpStatusCode.ServiceUnavailable, client.get("http://127.0.0.1:$port/v1/info").status)
            socket.setSoLinger(true, 0)
            socket.close()
            withTimeout(2_000) { finished.await() }
            // Reacquisition also checks that cleanup releases the deferred call permit.
            withTimeout(5_000) {
                while (client.get("http://127.0.0.1:$port/v1/info").status == HttpStatusCode.ServiceUnavailable) {
                    yield()
                }
            }
        } finally {
            socket?.close()
            client.close()
            server.stop(0, 1_000)
            source.close()
        }
    }

    @Test
    fun disconnectDuringSubscriptionSetupReleasesCallAndFlowPermits() = runBoundedIntegrationTest<Unit> {
        val source = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val entered = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val setups = AtomicInteger()
        val served = object : IsDataStore by source {
            override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
                request: RQ,
            ): Flow<IsUpdateResponse<DM>> {
                if (setups.incrementAndGet() > 1) return source.executeFlow(request)
                try {
                    entered.complete(Unit)
                    awaitCancellation()
                } finally {
                    finished.complete(Unit)
                }
            }
        }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            remoteStoreModule(served, limits = RemoteStoreServerLimits(maxConcurrentCalls = 1, maxConcurrentFlows = 1))
        }.start(wait = false)
        val client = createDefaultHttpClient()
        var socket: Socket? = null
        try {
            val port = server.engine.resolvedConnectors().single().port
            val context = RequestContext(DefinitionsContext(dataModels = mutableMapOf(
                SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel),
            )))
            val payload = RemoteStoreCodec.encode(
                Requests.Serializer,
                Requests(SimpleMarykModel.scan(allowTableScan = true)),
                context,
            )
            socket = withContext(Dispatchers.IO) {
                Socket("127.0.0.1", port).also {
                    it.getOutputStream().apply {
                        write(("POST ${RemoteStoreProtocol.flowPath} HTTP/1.1\r\n" +
                            "Host: 127.0.0.1:$port\r\n" +
                            "Content-Type: ${RemoteStoreProtocol.contentType}\r\n" +
                            "Content-Length: ${payload.size}\r\n" +
                            "${RemoteStoreProtocol.valueStorageFormatHeader}: ${StorageFormat.CURRENT}\r\n\r\n").encodeToByteArray())
                        write(payload)
                        flush()
                    }
                }
            }
            withTimeout(5_000) { entered.await() }
            socket.setSoLinger(true, 0)
            socket.close()
            withTimeout(2_000) { finished.await() }
            withTimeout(5_000) {
                while (client.get("http://127.0.0.1:$port/v1/info").status == HttpStatusCode.ServiceUnavailable) yield()
            }
            // A second subscription reaches setup only if both permits were returned.
            val remote = RemoteDataStore.connect(RemoteStoreConfig("http://127.0.0.1:$port"))
            try {
                withTimeout(5_000) { remote.executeFlow(SimpleMarykModel.scan(allowTableScan = true)).first() }
                assertEquals(2, setups.get())
            } finally { remote.close() }
        } finally {
            socket?.close()
            client.close()
            server.stop(0, 1_000)
            source.close()
        }
    }

    @Test
    fun disconnectDoesNotCancelAnAcceptedMutation() = runBoundedIntegrationTest<Unit> {
        val source = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val entered = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val served = object : IsDataStore by source {
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP {
                try {
                    entered.complete(Unit)
                    proceed.await()
                    return source.execute(request)
                } finally { finished.complete(Unit) }
            }
        }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) { remoteStoreModule(served) }.start(false)
        var socket: Socket? = null
        try {
            val port = server.engine.resolvedConnectors().single().port
            val context = RequestContext(DefinitionsContext(dataModels = mutableMapOf(
                SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel),
            )))
            val payload = RemoteStoreCodec.encode(Requests.Serializer,
                Requests(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha committed after disconnect" })), context)
            socket = withContext(Dispatchers.IO) {
                Socket("127.0.0.1", port).also {
                    it.getOutputStream().apply {
                        write(("POST ${RemoteStoreProtocol.executePath} HTTP/1.1\r\n" +
                            "Host: 127.0.0.1:$port\r\n" +
                            "Content-Type: ${RemoteStoreProtocol.contentType}\r\n" +
                            "Content-Length: ${payload.size}\r\n" +
                            "${RemoteStoreProtocol.valueStorageFormatHeader}: ${StorageFormat.CURRENT}\r\n\r\n").encodeToByteArray())
                        write(payload)
                        flush()
                    }
                }
            }
            withTimeout(5_000) { entered.await() }
            socket.setSoLinger(true, 0)
            socket.close()
            assertNull(withTimeoutOrNull(200) { finished.await() }, "Disconnect must not cancel accepted mutations")
            proceed.complete(Unit)
            withTimeout(5_000) { finished.await() }
            val rows: ValuesResponse<SimpleMarykModel> = source.execute(SimpleMarykModel.scan(allowTableScan = true))
            assertEquals("ha committed after disconnect", rows.values.single().values { value })
        } finally {
            proceed.complete(Unit)
            socket?.close()
            server.stop(0, 1_000)
            source.close()
        }
    }

}

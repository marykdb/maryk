package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.models.IsRootDataModel
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.DataStoreStateBackupChunk
import maryk.datastore.shared.DataStoreStateBackupManifest
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.DataStoreStateBackupWriter
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.StateBackupDataStore
import maryk.datastore.shared.backupState
import maryk.datastore.test.SerializedStateBackup
import maryk.datastore.test.advancedStateBackupModels
import maryk.datastore.test.assertAdvancedStateRoundTrip
import maryk.datastore.test.assertDeletedStateAllocations
import maryk.datastore.test.assertStateBatchRollback
import maryk.datastore.test.stateBackupModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

internal fun runStateBackupIntegrationTest(block: suspend CoroutineScope.() -> Unit) = runTest(timeout = 3.minutes) {
    withContext(Dispatchers.Default) { block() }
}

internal suspend fun <T> withRemoteStateStore(
    store: IsDataStore,
    config: RemoteStoreServerConfig = RemoteStoreServerConfig(),
    limits: RemoteStoreServerLimits = RemoteStoreServerLimits(),
    block: suspend (RemoteDataStore, HttpClient, String) -> T,
): T {
    val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) { remoteStoreModule(store, config, limits) }.start(wait = false)
    val client = HttpClient(CIO)
    try {
        val port = server.engine.resolvedConnectors().single().port
        val url = "http://127.0.0.1:$port"
        val remote = RemoteDataStore.connect(RemoteStoreConfig(url, httpClient = client))
        try { return block(remote, client, url) } finally { remote.close() }
    } finally { client.close(); server.stop(100, 500) }
}

/** Use shared application definitions when comparing values from separate decoded remote schemas. */
internal fun typedStateBackupView(remote: RemoteDataStore, models: Map<UInt, IsRootDataModel>): IsDataStore =
    object : IsDataStore by remote, StateBackupDataStore by remote {
        override val dataModelsById = models
    }

class RemoteStateBackupConformanceTest {
    @Test
    fun realHttpStateRoundTripPreservesComplexValuesCountersAndAtomicity() = runStateBackupIntegrationTest {
        val source = InMemoryDataStore.open(dataModelsById = advancedStateBackupModels)
        val target = InMemoryDataStore.open(dataModelsById = advancedStateBackupModels.mapKeys { it.key + 10u })
        try {
            withRemoteStateStore(source) { remoteSource, _, _ ->
                withRemoteStateStore(target) { remoteTarget, _, _ ->
                    val typedSource = typedStateBackupView(remoteSource, advancedStateBackupModels)
                    val typedTarget = typedStateBackupView(remoteTarget, advancedStateBackupModels.mapKeys { it.key + 10u })
                    assertStateBatchRollback(typedTarget)
                    assertAdvancedStateRoundTrip(typedSource, typedTarget)
                    assertDeletedStateAllocations(typedTarget)
                }
            }
        } finally { source.close(); target.close() }
    }

    @Test
    fun closingTheClientReleasesAServerCaptureWhileKeepingTheSuppliedHttpClientOpen() = runStateBackupIntegrationTest {
        val source = InMemoryDataStore.open(dataModelsById = stateBackupModels)
        val entered = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val received = CompletableDeferred<Unit>()
        val served = object : IsDataStore by source, StateBackupDataStore by source {
            override suspend fun writeStateBackup(writer: DataStoreStateBackupWriter, options: DataStoreStateBackupOptions): DataStoreStateBackupManifest {
                try {
                    return source.writeStateBackup(object : DataStoreStateBackupWriter by writer {
                        override suspend fun begin(manifest: DataStoreStateBackupManifest) {
                            writer.begin(manifest)
                            entered.complete(Unit)
                            awaitCancellation()
                        }
                    }, options)
                } finally { finished.complete(Unit) }
            }
        }
        try {
            withRemoteStateStore(served) { remote, client, url ->
                val capture = async { runCatching {
                    val fixture = SerializedStateBackup(remote)
                    remote.backupState(object : DataStoreStateBackupWriter by fixture {
                        override suspend fun begin(manifest: DataStoreStateBackupManifest) {
                            fixture.begin(manifest)
                            received.complete(Unit)
                        }
                    })
                } }
                entered.await()
                received.await()
                remote.close()
                assertTrue(withTimeout(5_000) { capture.await() }.isFailure)
                withTimeout(5_000) { finished.await() }
                assertEquals(HttpStatusCode.OK, client.get(url + RemoteStoreProtocol.infoPath).status)
            }
        } finally { source.close() }
    }
}

package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.StorageFormat
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.DataStoreStateBackupChunk
import maryk.datastore.shared.DataStoreStateBackupManifest
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.DataStoreStateBackupWriter
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.StateBackupArchiveWriter
import maryk.datastore.shared.StateBackupDataStore
import maryk.datastore.shared.backup
import maryk.datastore.shared.backupState
import maryk.datastore.shared.restore
import maryk.datastore.test.AtomicRestoreBackup
import maryk.datastore.test.DataStoreStateBackupTest
import maryk.datastore.test.SerializedStateBackup
import maryk.datastore.test.stateBackupModels
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteStateBackupTest {
    @Test
    fun streamsStateAndRestoresSeveralBatchesOfTheSameModel() = runBoundedIntegrationTest<Unit> {
        for (history in listOf(false, true)) {
            val source = InMemoryDataStore.open(dataModelsById = stateBackupModels, keepAllVersions = history)
            val target = InMemoryDataStore.open(dataModelsById = stateBackupModels.mapKeys { it.key + 10u })
            try {
                withRemote(source) { remoteSource, _, _ ->
                    withRemote(target) { remoteTarget, _, _ ->
                        assertTrue(remoteSource.supportsStateBackup)
                        DataStoreStateBackupTest(remoteSource, remoteTarget).restoresStateWithoutHistory()
                    }
                }
            } finally { source.close(); target.close() }
        }
    }

    @Test
    fun olderServersRefuseStateBeforeStartingTheWriter() = runBoundedIntegrationTest<Unit> {
        val source = InMemoryDataStore.open(dataModelsById = stateBackupModels)
        try {
            val olderStore = object : IsDataStore by source {}
            withRemote(olderStore) { remote, _, _ ->
                assertFalse(remote.supportsStateBackup)
                val fixture = SerializedStateBackup(remote)
                assertFailsWith<RequestException> { remote.backupState(fixture) }
                assertTrue(fixture.chunks.isEmpty())
                assertFalse(fixture.completed)
            }
        } finally { source.close() }
    }

    @Test
    fun authorizesEveryExportModelBeforeSendingTheManifest() = runBoundedIntegrationTest<Unit> {
        val source = InMemoryDataStore.open(dataModelsById = stateBackupModels)
        var began = false
        val config = RemoteStoreServerConfig(authorizer = RemoteStoreAuthorizer {
            it.operation != RemoteStoreOperation.StateBackup || it.modelName == SimpleMarykModel.Meta.name
        })
        try {
            withRemote(source, config) { remote, _, _ ->
                assertFailsWith<IllegalStateException> {
                    remote.backupState(object : DataStoreStateBackupWriter {
                        override suspend fun begin(manifest: DataStoreStateBackupManifest) { began = true }
                        override suspend fun write(chunk: DataStoreStateBackupChunk) = Unit
                        override suspend fun complete() = Unit
                    })
                }
                assertFalse(began)
            }
        } finally { source.close() }
    }

    @Test
    fun validatesCompletionAndPermissionsBeforeAnyRemoteStateMutation() = runBoundedIntegrationTest<Unit> {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(dataModelsById = models)
        val target = InMemoryDataStore.open(dataModelsById = models)
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha state batch" }))
            @Suppress("UNCHECKED_CAST")
            val records = source.execute(SimpleMarykModel.scan(allowTableScan = true)).values as List<ValuesWithMetaData<IsRootDataModel>>
            val chunk = DataStoreStateBackupChunk(SimpleMarykModel.Meta.name, records)
            val valid = encodeStateRestoreBatch(SimpleMarykModel, chunk)
            withRemote(target) { _, client, url ->
                for (invalid in listOf(valid.copyOf(valid.size - 1), valid + byteArrayOf(1), valid.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })) {
                    assertEquals(HttpStatusCode.BadRequest, postBatch(client, url, invalid))
                    assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
                }
                // Two complete chunks are not one atomic restore request.
                val frames = mutableListOf<ByteArray>()
                val writer = StateBackupArchiveWriter(models, { frames += it }, {})
                writer.begin(DataStoreStateBackupManifest(listOf(SimpleMarykModel.Meta.name), mapOf(SimpleMarykModel.Meta.name to SimpleMarykModel.Meta.version.major)))
                writer.write(chunk)
                writer.write(chunk)
                writer.complete()
                assertEquals(HttpStatusCode.BadRequest, postBatch(client, url, frames.reduce { a, b -> a + b }))
                assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
            }
            for (operation in listOf(RemoteStoreOperation.StateRestore, RemoteStoreOperation.ProcessUpdate)) {
                withRemote(target, RemoteStoreServerConfig(authorizer = RemoteStoreAuthorizer { it.operation != operation })) { _, client, url ->
                    assertEquals(HttpStatusCode.Forbidden, postBatch(client, url, valid))
                    assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
                }
            }
        } finally { source.close(); target.close() }
    }

    @Test
    fun clientCloseCancelsStateOperationsWithoutClosingAnApplicationOwnedHttpClient() = runBoundedIntegrationTest<Unit> {
        val source = InMemoryDataStore.open(dataModelsById = stateBackupModels)
        val entered = CompletableDeferred<Unit>()
        try {
            withRemote(source) { remote, client, url ->
                val capture = async {
                    runCatching { remote.backupState(object : DataStoreStateBackupWriter {
                        override suspend fun begin(manifest: DataStoreStateBackupManifest) { entered.complete(Unit); awaitCancellation() }
                        override suspend fun write(chunk: DataStoreStateBackupChunk) = Unit
                        override suspend fun complete() = error("Cancelled export was completed")
                    }) }
                }
                entered.await()
                remote.close()
                assertTrue(withTimeout(5_000) { capture.await() }.isFailure)
                assertEquals(HttpStatusCode.OK, client.get(url + RemoteStoreProtocol.infoPath).status)
            }
        } finally { source.close() }
    }

    @Test
    fun writerFailureCancelsTheServerCaptureAndReleasesSourceWrites() = runBoundedIntegrationTest<Unit> {
        val source = InMemoryDataStore.open(dataModelsById = stateBackupModels)
        source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha cancellation" }))
        val finished = CompletableDeferred<Unit>()
        val served = object : IsDataStore by source, StateBackupDataStore by source {
            override suspend fun writeStateBackup(writer: DataStoreStateBackupWriter, options: DataStoreStateBackupOptions): DataStoreStateBackupManifest {
                try {
                    return source.writeStateBackup(object : DataStoreStateBackupWriter by writer {
                        override suspend fun write(chunk: DataStoreStateBackupChunk) {
                            writer.write(chunk)
                            awaitCancellation()
                        }
                    }, options)
                } finally { finished.complete(Unit) }
            }
        }
        try {
            withRemote(served) { remote, _, _ ->
                assertFails { remote.backupState(object : DataStoreStateBackupWriter {
                    override suspend fun begin(manifest: DataStoreStateBackupManifest) = Unit
                    override suspend fun write(chunk: DataStoreStateBackupChunk) = error("Destination failed")
                    override suspend fun complete() = error("Failed export was completed")
                }) }
                withTimeout(5_000) { finished.await() }
                withTimeout(5_000) { source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha source is usable" })) }
            }
        } finally { source.close() }
    }

    @Test
    fun streamedCaptureHoldsAdmissionUntilCancellationAndThenReleasesIt() = runBoundedIntegrationTest<Unit> {
        val source = InMemoryDataStore.open(dataModelsById = stateBackupModels)
        val entered = CompletableDeferred<Unit>()
        val received = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
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
            withRemote(served, limits = RemoteStoreServerLimits(maxConcurrentCalls = 1)) { remote, client, url ->
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
                assertEquals(HttpStatusCode.ServiceUnavailable, client.get(url + RemoteStoreProtocol.infoPath).status)
                capture.cancelAndJoin()
                withTimeout(5_000) { finished.await() }
                withTimeout(5_000) {
                    while (client.get(url + RemoteStoreProtocol.infoPath).status == HttpStatusCode.ServiceUnavailable) delay(10)
                }
                assertEquals(HttpStatusCode.OK, client.get(url + RemoteStoreProtocol.infoPath).status)
            }
        } finally { source.close() }
    }

    @Test
    fun remoteSourceAndTargetAlsoRoundTripHistoryWithANegotiatedSnapshot() = runBoundedIntegrationTest<Unit> {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(dataModelsById = models, keepAllVersions = true)
        val target = InMemoryDataStore.open(dataModelsById = models, keepAllVersions = true)
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha remote history" }))
            withRemote(source) { remoteSource, _, _ ->
                withRemote(target) { remoteTarget, _, _ ->
                    val backup = AtomicRestoreBackup()
                    remoteSource.backup(backup)
                    assertEquals(1uL, remoteTarget.restore(backup).records)
                    assertEquals(source.execute(SimpleMarykModel.scan(allowTableScan = true)).values, target.execute(SimpleMarykModel.scan(allowTableScan = true)).values)
                }
            }
        } finally { source.close(); target.close() }
    }

    @Test
    fun incompleteStreamsNeverPublishAndInvalidAcknowledgementsFail() = runBoundedIntegrationTest<Unit> {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(dataModelsById = models)
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha truncated export" }))
            val frames = mutableListOf<ByteArray>()
            source.backupState(StateBackupArchiveWriter(models, { frames += it }, {}))
            val bytes = frames.reduce { a, b -> a + b }.let { it.copyOf(it.size - 1) }
            val info = RemoteStoreInfo(RemoteDataStore.collectDefinitions(models.values), listOf(RemoteStoreModelId(1u, SimpleMarykModel.Meta.name)), false, false, false, supportsStateBackup = true)
            val infoBytes = RemoteStoreCodec.encode(RemoteStoreInfo.Serializer, info, DefinitionsConversionContext())
            val client = HttpClient(MockEngine { request ->
                when (request.url.encodedPath) {
                    RemoteStoreProtocol.infoPath -> respond(infoBytes, headers = headersOf(HttpHeaders.ContentType, RemoteStoreProtocol.contentType))
                    RemoteStoreProtocol.stateBackupPath -> respond(RemoteStoreCodec.lengthPrefix(bytes.size) + bytes, headers = headersOf(HttpHeaders.ContentType, RemoteStoreProtocol.stateStreamContentType))
                    else -> respond(byteArrayOf(), headers = headersOf(HttpHeaders.ContentType, RemoteStoreProtocol.contentType))
                }
            })
            val remote = RemoteDataStore.connect(RemoteStoreConfig("http://localhost:8210", httpClient = client))
            try {
                val fixture = SerializedStateBackup(remote)
                assertFails { remote.backupState(fixture) }
                assertFalse(fixture.completed)
                @Suppress("UNCHECKED_CAST")
                val records = source.execute(SimpleMarykModel.scan(allowTableScan = true)).values as List<ValuesWithMetaData<IsRootDataModel>>
                assertFailsWith<RequestException> { remote.restoreStateVersion(SimpleMarykModel, records, emptyMap()) }
            } finally { remote.close(); client.close() }
        } finally { source.close() }
    }

    private suspend fun postBatch(client: HttpClient, url: String, bytes: ByteArray): HttpStatusCode = client.post(url + RemoteStoreProtocol.stateRestorePath) {
        header(HttpHeaders.ContentType, RemoteStoreProtocol.stateArchiveContentType)
        header(RemoteStoreProtocol.valueStorageFormatHeader, StorageFormat.CURRENT.toString())
        setBody(bytes)
    }.status

    private suspend fun <T> withRemote(
        store: IsDataStore,
        config: RemoteStoreServerConfig = RemoteStoreServerConfig(),
        limits: RemoteStoreServerLimits = RemoteStoreServerLimits(),
        block: suspend (RemoteDataStore, HttpClient, String) -> T,
    ): T {
        return withRemoteStateStore(store, config, limits, block)
    }
}

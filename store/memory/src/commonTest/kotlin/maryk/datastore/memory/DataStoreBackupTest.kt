package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupReader
import maryk.datastore.shared.DataStoreBackupWriter
import maryk.datastore.shared.DataStoreRestoreOptions
import maryk.datastore.shared.RepeatableDataStoreBackupReader
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.backup
import maryk.datastore.shared.captureSnapshotVersion
import maryk.datastore.shared.restore
import maryk.datastore.test.assertStatusIs
import maryk.datastore.test.UniqueModel
import maryk.test.models.SimpleMarykModel
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataStoreBackupTest {
    @Test
    fun oversizedLateVersionIsRejectedBeforeAnyReplay() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val target = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha small" }))
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha ${"x".repeat(1024)}" }))
            val backup = CollectingBackup()
            source.backup(backup, batchSize = 1u)
            val repeatable = object : RepeatableDataStoreBackupReader {
                override val manifest get() = backup.manifest
                override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) = backup.read(consumer)
            }
            for (reader in listOf(backup, repeatable)) {
                assertFailsWith<RequestException> {
                    target.restore(reader, true, DataStoreRestoreOptions(maxReplayBytes = 512))
                }
                assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
            }
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun repeatableRestoreBoundsEncodedBatchesWithoutStagingInput() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val target = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        var requests = 0
        var passes = 0
        val boundedTarget = object : IsDataStore by target {
            override suspend fun <DM : IsRootDataModel> processUpdate(updateResponse: UpdateResponse<DM>): ProcessResponse<DM> {
                val size = UpdateResponse.Serializer.calculateObjectProtoBufLength(
                    updateResponse, WriteCache(),
                    RequestContext(DefinitionsContext(mutableMapOf(SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel))), SimpleMarykModel),
                )
                assertTrue(size <= 1024, "Restore exceeded transport bound: $size")
                requests++
                return target.processUpdate(updateResponse)
            }
        }
        try {
            repeat(25) { index ->
                source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha $index ${"x".repeat(200)}" }))
            }
            val backup = CollectingBackup()
            source.backup(backup, batchSize = 2u)
            val repeatable = object : RepeatableDataStoreBackupReader {
                override val manifest get() = backup.manifest
                override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) {
                    passes++
                    backup.read(consumer)
                }
            }
            assertEquals(25uL, boundedTarget.restore(repeatable, true,
                DataStoreRestoreOptions(maxStagedBytes = 1, maxStagedRecords = 1, maxReplayBytes = 1024)).records)
            assertTrue(passes > 2)
            assertTrue(requests > 1)
            assertEquals(25, target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun onePassRestoreRejectsStagingOverflowBeforeMutation() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val target = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        try {
            repeat(2) { index -> source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha $index" })) }
            val backup = CollectingBackup()
            source.backup(backup, batchSize = 1u)
            assertFailsWith<RequestException> {
                target.restore(backup, true, DataStoreRestoreOptions(maxStagedBytes = 1))
            }
            assertFailsWith<RequestException> {
                target.restore(backup, true, DataStoreRestoreOptions(maxStagedRecords = 1))
            }
            assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun restoreBoundsReplicationRequests() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val target = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        var requests = 0
        val boundedTarget = object : IsDataStore by target {
            override suspend fun <DM : IsRootDataModel> processUpdate(updateResponse: UpdateResponse<DM>): ProcessResponse<DM> {
                val update = updateResponse.update as InitialChangesUpdate<DM>
                assertTrue(update.changes.size <= 128, "Restore accumulated a whole model in one request")
                requests++
                return target.processUpdate(updateResponse)
            }
        }
        try {
            repeat(260) { index ->
                source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha $index" }))
            }
            val backup = CollectingBackup()
            source.backup(backup, batchSize = 10u)
            assertEquals(260uL, boundedTarget.restore(backup).records)
            assertTrue(requests >= 3)
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun automaticSnapshotRequiresAuthoritativeProvider() = runTest {
        val source = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        try {
            val storeWithoutProvider = object : IsDataStore by source {}
            assertFailsWith<RequestException> {
                storeWithoutProvider.captureSnapshotVersion()
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun readsDoNotReserveAdditionalSnapshotVersions() = runTest {
        val source = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        try {
            source.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "ha" })
            )
            val beforeRead = source.captureSnapshotVersion()

            source.execute(SimpleMarykModel.scan(allowTableScan = true))

            assertEquals(beforeRead + 1uL, source.captureSnapshotVersion())
        } finally {
            source.close()
        }
    }

    @Test
    fun snapshotReservationExcludesLaterMutation() = runTest {
        val source = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        try {
            val snapshotVersion = source.captureSnapshotVersion()
            val add = source.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "haha" })
            )
            val addVersion = assertStatusIs<AddSuccess<SimpleMarykModel>>(add.statuses.single()).version

            assertTrue(addVersion > snapshotVersion)
            assertTrue(
                source.execute(
                    SimpleMarykModel.scan(
                        toVersion = snapshotVersion,
                        allowTableScan = true,
                    )
                ).values.isEmpty()
            )
        } finally {
            source.close()
        }
    }

    @Test
    fun pointInTimeBackupRestoresCapturedVersion() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val target = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        try {
            val add = source.execute(
                SimpleMarykModel.add(
                    SimpleMarykModel.create { value with "ha before backup" }
                )
            )
            val key = assertStatusIs<AddSuccess<SimpleMarykModel>>(add.statuses.single()).key
            val snapshotVersion = source.captureSnapshotVersion()

            source.execute(
                SimpleMarykModel.change(
                    key.change(Change(SimpleMarykModel { value::ref } with "ha after backup"))
                )
            )

            val writer = CollectingBackup()
            source.backup(writer, snapshotVersion = snapshotVersion, batchSize = 1u)
            val result = target.restore(writer)

            assertEquals(1uL, result.records)
            assertEquals(
                "ha before backup",
                target.execute(SimpleMarykModel.scan(allowTableScan = true))
                    .values.single().values { value },
            )
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun restoreRejectsMismatchedMajorModelVersion() = runTest {
        val source = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        val target = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to IncompatibleSimpleMarykModel),
        )
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha" }))
            val writer = CollectingBackup()
            source.backup(writer)

            assertFailsWith<RequestException> { target.restore(writer) }
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun restoreRejectsTargetWithoutVersionHistory() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val target = InMemoryDataStore.open(keepAllVersions = false, dataModelsById = models)
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha" }))
            val writer = CollectingBackup()
            source.backup(writer)

            assertFailsWith<RequestException> { target.restore(writer) }
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun restoreRejectsUnexpectedProcessResponse() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val target = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val invalidTarget = object : IsDataStore by target {
            override suspend fun <DM : IsRootDataModel> processUpdate(
                updateResponse: UpdateResponse<DM>,
            ) = ProcessResponse(
                updateResponse.update.version,
                ValuesResponse(updateResponse.dataModel, emptyList()),
            )
        }
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha" }))
            val writer = CollectingBackup()
            source.backup(writer)

            assertFailsWith<RequestException> { invalidTarget.restore(writer) }
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun restoreRejectsHistoryAtOrBeyondSnapshotVersion() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val target = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha" }))
            val writer = CollectingBackup()
            source.backup(writer)
            val chunk = writer.chunks.single()
            val record = chunk.records.single()
            writer.chunks[0] = chunk.copy(
                records = listOf(
                    record.copy(
                        changes = record.changes.mapIndexed { index, change ->
                            if (index == record.changes.lastIndex) {
                                change.copy(version = writer.manifest.snapshotVersion)
                            } else {
                                change
                            }
                        }
                    )
                )
            )

            assertFailsWith<RequestException> { target.restore(writer) }
            assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun backupIncludesHistoryOlderThanMaxVersionsWindow() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        try {
            val add = source.execute(
                SimpleMarykModel.add(
                    SimpleMarykModel.create { value with "ha initial" }
                )
            )
            val key = assertStatusIs<AddSuccess<SimpleMarykModel>>(add.statuses.single()).key
            repeat(1_001) { index ->
                source.execute(
                    SimpleMarykModel.change(
                        key.change(Change(SimpleMarykModel { value::ref } with "ha change $index"))
                    )
                )
            }

            val writer = CollectingBackup()
            source.backup(writer, batchSize = 1u)
            val history = writer.chunks.single().records.single().changes

            assertEquals(1_002, history.size)
            assertEquals(
                "ha initial",
                history.first().changes
                    .filterIsInstance<Change>()
                    .flatMap { it.referenceValuePairs }
                    .single { it.reference == SimpleMarykModel { value::ref } }
                    .value,
            )
        } finally {
            source.close()
        }
    }

    @Test
    fun backupRejectsRecordHistoryBeyondConfiguredLimit() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        try {
            val add = source.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "ha initial" })
            )
            val key = assertStatusIs<AddSuccess<SimpleMarykModel>>(add.statuses.single()).key
            repeat(2) { index ->
                source.execute(
                    SimpleMarykModel.change(
                        key.change(Change(SimpleMarykModel { value::ref } with "ha change $index"))
                    )
                )
            }

            assertFailsWith<RequestException> {
                source.backup(CollectingBackup(), null, 250u, 2u)
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun backupIncludesRecordBeforeHardDeleteButExcludesItAfterward() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val beforeDeleteTarget = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val afterDeleteTarget = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        try {
            val add = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha hard delete" })).statuses.single()
            )
            val beforeDeleteSnapshot = source.captureSnapshotVersion()
            val beforeDelete = CollectingBackup()
            val delete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                source.execute(SimpleMarykModel.delete(add.key, hardDelete = true)).statuses.single()
            )
            val afterDelete = CollectingBackup()

            source.backup(beforeDelete, snapshotVersion = beforeDeleteSnapshot)
            source.backup(afterDelete, snapshotVersion = delete.version + 1uL)

            assertEquals(1uL, beforeDeleteTarget.restore(beforeDelete).records)
            assertEquals(0uL, afterDeleteTarget.restore(afterDelete).records)
        } finally {
            source.close()
            beforeDeleteTarget.close()
            afterDeleteTarget.close()
        }
    }

    @Test
    fun backupRejectsHardDeletedAndRecreatedKeyBeforeCompletion() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        try {
            val key = SimpleMarykModel.key(validUuidV4Bytes(1))
            source.execute(SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha original" }))
            source.execute(SimpleMarykModel.delete(key, hardDelete = true))
            source.execute(SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha replacement" }))
            val backup = CollectingBackup()

            assertFailsWith<RequestException> { source.backup(backup) }
            assertTrue(!backup.completed)
        } finally {
            source.close()
        }
    }

    @Test
    fun restoreReplaysUniqueValueTransferChronologicallyAcrossChunks() = runTest {
        val models = mapOf(1u to UniqueModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val target = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        try {
            val ownerKey = UniqueModel.key(validUuidV4Bytes(2))
            val receiverKey = UniqueModel.key(validUuidV4Bytes(1))
            source.execute(UniqueModel.add(ownerKey to UniqueModel.create { email with "transfer@test.com" }))
            source.execute(
                UniqueModel.change(ownerKey.change(Change(UniqueModel { email::ref } with "released@test.com")))
            )
            source.execute(UniqueModel.add(receiverKey to UniqueModel.create { email with "transfer@test.com" }))
            val backup = CollectingBackup()

            source.backup(backup, batchSize = 1u)

            assertEquals(2uL, target.restore(backup).records)
            assertEquals(
                "released@test.com",
                target.execute(UniqueModel.get(ownerKey)).values.single().values { email },
            )
            assertEquals(
                "transfer@test.com",
                target.execute(UniqueModel.get(receiverKey)).values.single().values { email },
            )
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun interruptedRestoreDoesNotApplyStagedChunks() = runTest {
        val models = mapOf(1u to SimpleMarykModel)
        val source = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        val target = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = models)
        try {
            source.execute(
                SimpleMarykModel.add(
                    SimpleMarykModel.create { value with "ha first chunk" },
                    SimpleMarykModel.create { value with "ha second chunk" },
                )
            )
            val backup = CollectingBackup()
            source.backup(backup, batchSize = 1u)
            assertEquals(2, backup.chunks.size)

            assertFailsWith<RequestException> {
                target.restore(FailAfterFirstChunkBackup(backup))
            }
            assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        } finally {
            source.close()
            target.close()
        }
    }
}

private object IncompatibleSimpleMarykModel : RootDataModel<IncompatibleSimpleMarykModel>(
    name = SimpleMarykModel.Meta.name,
    version = Version(2),
) {
    val value by string(index = 1u, default = "haha", regEx = "ha.*")
}

private class CollectingBackup : DataStoreBackupWriter, DataStoreBackupReader {
    private lateinit var storedManifest: DataStoreBackupManifest
    val chunks = mutableListOf<DataStoreBackupChunk>()
    var completed = false

    override val manifest: DataStoreBackupManifest
        get() = storedManifest

    override suspend fun begin(manifest: DataStoreBackupManifest) {
        storedManifest = manifest
    }

    override suspend fun write(chunk: DataStoreBackupChunk) {
        chunks += chunk
    }

    override suspend fun complete() {
        completed = true
    }

    override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) {
        chunks.forEach { consumer(it) }
    }
}

private fun validUuidV4Bytes(value: Int): ByteArray =
    ByteArray(16) { value.toByte() }.apply {
        this[6] = ((this[6].toInt() and 0x0F) or 0x40).toByte()
        this[8] = ((this[8].toInt() and 0x3F) or 0x80).toByte()
    }

private class FailAfterFirstChunkBackup(
    private val backup: CollectingBackup,
) : DataStoreBackupReader {
    override val manifest: DataStoreBackupManifest
        get() = backup.manifest

    override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) {
        backup.chunks.forEachIndexed { index, chunk ->
            if (index == 1) throw RequestException("Simulated interruption while reading backup")
            consumer(chunk)
        }
    }
}

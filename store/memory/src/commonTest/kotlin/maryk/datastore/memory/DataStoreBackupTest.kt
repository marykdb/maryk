package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupReader
import maryk.datastore.shared.DataStoreBackupWriter
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.backup
import maryk.datastore.shared.captureSnapshotVersion
import maryk.datastore.shared.restore
import maryk.datastore.test.assertStatusIs
import maryk.test.models.SimpleMarykModel
import maryk.core.query.responses.statuses.AddSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataStoreBackupTest {
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

    override val manifest: DataStoreBackupManifest
        get() = storedManifest

    override suspend fun begin(manifest: DataStoreBackupManifest) {
        storedManifest = manifest
    }

    override suspend fun write(chunk: DataStoreBackupChunk) {
        chunks += chunk
    }

    override suspend fun complete() = Unit

    override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) {
        chunks.forEach { consumer(it) }
    }
}

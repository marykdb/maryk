package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scanChanges
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupReader
import maryk.datastore.shared.DataStoreBackupWriter
import maryk.datastore.shared.backup
import maryk.datastore.shared.restore
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IndexedDbReplicationHistoryRegressionTest {
    @Test
    fun replicatedNoOpAdvancesVersionBeforeAnOlderChangeArrives() = runTest {
        installIndexedDbForTests()
        val databaseName = "maryk-idb-no-op-replay-${Random.nextInt()}"
        var store = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
        val key = Key<SimpleMarykModel>(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 7 })
        val initialValues = SimpleMarykModel.create { value with "haha-a" }
        try {
            store.processUpdate(UpdateResponse(SimpleMarykModel, AdditionUpdate(key, 10uL, 10uL, 0, false, initialValues)))
            store.processUpdate(UpdateResponse(SimpleMarykModel, ChangeUpdate(
                key, 30uL, 0, listOf(Change(SimpleMarykModel.ref { value } with "haha-a")),
            )))
            assertEquals(30uL, store.execute(SimpleMarykModel.get(key)).values.single().lastVersion)

            store.close()
            store = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
            store.processUpdate(UpdateResponse(SimpleMarykModel, ChangeUpdate(
                key, 20uL, 0, listOf(Change(SimpleMarykModel.ref { value } with "haha-b")),
            )))
            val record = store.execute(SimpleMarykModel.get(key)).values.single()
            assertEquals(initialValues, record.values)
            assertEquals(30uL, record.lastVersion)
        } finally {
            store.close()
        }
    }

    @Test
    fun backupRetainsCreationFieldsNotChangedByLaterEvents() = runTest {
        installIndexedDbForTests()
        val source = openHistoryStore("source")
        val target = openHistoryStore("target")
        try {
            val initial = ReplicationHistoryModel.create {
                changed with "initial"
                retained with "keep me"
            }
            val key = assertIs<AddSuccess<ReplicationHistoryModel>>(
                source.execute(ReplicationHistoryModel.add(initial)).statuses.single(),
            ).key
            source.execute(ReplicationHistoryModel.change(key.change(
                Change(ReplicationHistoryModel.ref { changed } with "updated"),
            )))

            val history = source.execute(ReplicationHistoryModel.getChanges(key, maxVersions = 10u)).changes.single()
            val retainedPairs = history.changes.flatMap { it.changes }.filterIsInstance<Change>()
                .flatMap { it.referenceValuePairs }
                .filter { it.reference == ReplicationHistoryModel.ref { retained } }
            assertEquals(listOf("keep me"), retainedPairs.map { it.value })

            val backup = MemoryBackup()
            source.backup(backup)
            target.restore(backup)
            assertEquals(
                source.execute(ReplicationHistoryModel.get(key)).values.single().values,
                target.execute(ReplicationHistoryModel.get(key)).values.single().values,
            )
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun historySelectionDoesNotRestoreExcludedChanges() = runTest {
        installIndexedDbForTests()
        val store = openHistoryStore("selection")
        try {
            val key = assertIs<AddSuccess<ReplicationHistoryModel>>(
                store.execute(ReplicationHistoryModel.add(ReplicationHistoryModel.create {
                    changed with "initial"
                    retained with "keep me"
                })).statuses.single(),
            ).key
            store.execute(ReplicationHistoryModel.change(key.change(
                Change(ReplicationHistoryModel.ref { changed } with "excluded"),
            )))
            val select = ReplicationHistoryModel.graph { listOf(retained) }
            val responses = listOf(
                store.execute(ReplicationHistoryModel.getChanges(key, select = select, maxVersions = 10u)),
                store.execute(ReplicationHistoryModel.scanChanges(select = select, maxVersions = 10u)),
            )
            for (response in responses) {
                val pairs = response.changes.single().changes.flatMap { it.changes }
                    .filterIsInstance<Change>().flatMap { it.referenceValuePairs }
                assertTrue(pairs.isNotEmpty())
                assertTrue(pairs.all { it.reference == ReplicationHistoryModel.ref { retained } })
            }
        } finally {
            store.close()
        }
    }

    private suspend fun openHistoryStore(suffix: String) = IndexedDbDataStore.open(
        databaseName = "maryk-idb-replay-history-$suffix-${Random.nextInt()}",
        dataModelsById = mapOf(1u to ReplicationHistoryModel),
        keepAllVersions = true,
    )
}

private object ReplicationHistoryModel : RootDataModel<ReplicationHistoryModel>() {
    val changed by string(1u, default = "default changed")
    val retained by string(2u, default = "default retained")
}

private class MemoryBackup : DataStoreBackupWriter, DataStoreBackupReader {
    override lateinit var manifest: DataStoreBackupManifest
    private val chunks = mutableListOf<DataStoreBackupChunk>()

    override suspend fun begin(manifest: DataStoreBackupManifest) {
        this.manifest = manifest
    }

    override suspend fun write(chunk: DataStoreBackupChunk) {
        chunks += chunk
    }

    override suspend fun complete() = Unit

    override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) {
        for (chunk in chunks) consumer(chunk)
    }
}

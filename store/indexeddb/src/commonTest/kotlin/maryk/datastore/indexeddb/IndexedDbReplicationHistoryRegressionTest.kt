package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanChanges
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupReader
import maryk.datastore.shared.DataStoreBackupWriter
import maryk.datastore.shared.backup
import maryk.datastore.shared.restore
import maryk.datastore.shared.updates.Update
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IndexedDbReplicationHistoryRegressionTest {
    @Test
    fun getUpdatesReconstructsEmbeddedAndMapCreationValuesWithoutHistory() = runTest {
        installIndexedDbForTests()
        val source = IndexedDbDataStore.open("maryk-embedded-creation-source-${Random.nextInt()}", mapOf(1u to EmbeddedCreationReplicationModel))
        val target = IndexedDbDataStore.open("maryk-embedded-creation-target-${Random.nextInt()}", mapOf(1u to EmbeddedCreationReplicationModel))
        try {
            val child = CreationReplicationDetails.create {
                name with "retained child"
                labels with mapOf("nested" to "map value")
            }
            val values = EmbeddedCreationReplicationModel.create {
                details with child
                children with mapOf("fixed" to child)
            }
            val key = assertIs<AddSuccess<EmbeddedCreationReplicationModel>>(source.execute(
                EmbeddedCreationReplicationModel.add(values)
            ).statuses.single()).key
            val addition = assertIs<AdditionUpdate<EmbeddedCreationReplicationModel>>(source.execute(
                EmbeddedCreationReplicationModel.getUpdates(key)
            ).updates.filterIsInstance<AdditionUpdate<*>>().single())
            assertEquals(values, addition.values)
            target.processUpdate(UpdateResponse(EmbeddedCreationReplicationModel, InitialChangesUpdate(addition.version,
                listOf(DataObjectVersionedChange(key, changes = listOf(VersionedChanges(addition.version,
                    listOf(ObjectCreate) + values.toChanges().toList(),
                )))),
            )))
            assertEquals(values, target.execute(EmbeddedCreationReplicationModel.get(key)).values.single().values)
        } finally { source.close(); target.close() }
    }

    @Test
    fun getUpdatesRetainsDeletionAtCreationWithoutHistoricSnapshots() = runTest {
        installIndexedDbForTests()
        val source = IndexedDbDataStore.open("maryk-deleted-get-updates-source-${Random.nextInt()}", mapOf(1u to EmptyDeletedReplicationModel))
        val target = IndexedDbDataStore.open("maryk-deleted-get-updates-target-${Random.nextInt()}", mapOf(1u to EmptyDeletedReplicationModel))
        try {
            val records = listOf(EmptyDeletedReplicationModel.create { }, EmptyDeletedReplicationModel.create { optional with "retained" })
            for ((index, values) in records.withIndex()) {
                val key = Key<EmptyDeletedReplicationModel>(ByteArray(EmptyDeletedReplicationModel.Meta.keyByteSize) { index.toByte() })
                source.processUpdate(UpdateResponse(EmptyDeletedReplicationModel, AdditionUpdate(key, 100uL, 100uL, 0, true, values)))
                val addition = assertIs<AdditionUpdate<EmptyDeletedReplicationModel>>(
                    source.execute(EmptyDeletedReplicationModel.getUpdates(key, filterSoftDeleted = false)).updates.filterIsInstance<AdditionUpdate<*>>().single()
                )
                assertTrue(addition.isDeleted)
                assertEquals(values, addition.values)
                target.processUpdate(UpdateResponse(EmptyDeletedReplicationModel, addition))
                assertTrue(target.execute(EmptyDeletedReplicationModel.get(key)).values.isEmpty())
                val retained = target.execute(EmptyDeletedReplicationModel.get(key, filterSoftDeleted = false)).values.single()
                assertTrue(retained.isDeleted)
                assertEquals(values, retained.values)
            }
        } finally { source.close(); target.close() }
    }

    @Test
    fun journalPreservesAnEmptyDeletedCreationSnapshot() = runTest {
        installIndexedDbForTests()
        val database = "maryk-empty-deleted-journal-${Random.nextInt()}"
        suspend fun open() = IndexedDbDataStore.open(database, mapOf(1u to EmptyDeletedReplicationModel))
        var store = open()
        try {
            val key = Key<EmptyDeletedReplicationModel>(ByteArray(EmptyDeletedReplicationModel.Meta.keyByteSize) { 8 })
            val values = EmptyDeletedReplicationModel.create { }
            store.processUpdate(UpdateResponse(EmptyDeletedReplicationModel, AdditionUpdate(key, 100uL, 100uL, 0, true, values)))
            val snapshot = assertNotNull(store.byteStore.get("k:1", key.bytes))
            val journal = encodeCommitJournalEntry(1u, Update.Addition(EmptyDeletedReplicationModel, key, 100uL, values, true), snapshot)
            val replayed = assertIs<Update.Addition<*>>(decodeCommitJournalEntry(
                mapOf(1u to EmptyDeletedReplicationModel), IndexedDbSensitiveFieldSupport(mapOf(1u to EmptyDeletedReplicationModel), null),
                createCommitJournalKey(100uL, 0u), journal,
            ))
            assertTrue(replayed.isDeleted)
            assertEquals(values, replayed.values)
            store.close()
            store = open()
            val retained = store.execute(EmptyDeletedReplicationModel.get(key, filterSoftDeleted = false)).values.single()
            assertTrue(retained.isDeleted)
            assertEquals(values, retained.values)
        } finally { store.close() }
    }

    @Test
    fun backupAndJournalPreserveDeletionAtCreation() = runTest {
        installIndexedDbForTests()
        val source = openHistoryStore("deleted-source")
        val target = openHistoryStore("deleted-target")
        try {
            val key = Key<ReplicationHistoryModel>(ByteArray(ReplicationHistoryModel.Meta.keyByteSize) { 9 })
            val values = ReplicationHistoryModel.create { changed with "deleted creation"; retained with "keep me" }
            source.processUpdate(UpdateResponse(ReplicationHistoryModel, AdditionUpdate(key, 100uL, 100uL, 0, true, values)))
            val history = source.execute(ReplicationHistoryModel.getChanges(key, filterSoftDeleted = false)).changes.single()
            assertEquals(listOf(ObjectSoftDeleteChange(true)), history.changes.single().changes.filterIsInstance<ObjectSoftDeleteChange>())

            val snapshot = source.byteStore.get("k:1", key.bytes)!!
            val journal = encodeCommitJournalEntry(1u, Update.Addition(ReplicationHistoryModel, key, 100uL, values, true), snapshot)
            val replayed = assertIs<Update.Addition<*>>(decodeCommitJournalEntry(
                mapOf(1u to ReplicationHistoryModel), IndexedDbSensitiveFieldSupport(mapOf(1u to ReplicationHistoryModel), null),
                createCommitJournalKey(100uL, 0u), journal,
            ))
            assertTrue(replayed.isDeleted)

            val backup = MemoryBackup()
            source.backup(backup)
            target.restore(backup)
            assertTrue(target.execute(ReplicationHistoryModel.get(key)).values.isEmpty())
            assertTrue(target.execute(ReplicationHistoryModel.get(key, filterSoftDeleted = false)).values.single().isDeleted)
        } finally {
            source.close()
            target.close()
        }
    }

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

private object EmptyDeletedReplicationModel : RootDataModel<EmptyDeletedReplicationModel>() {
    val optional by string(1u, required = false)
}

private object CreationReplicationDetails : DataModel<CreationReplicationDetails>() {
    val name by string(1u)
    val labels by map(2u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

private object EmbeddedCreationReplicationModel : RootDataModel<EmbeddedCreationReplicationModel>() {
    val details by embed(1u, dataModel = { CreationReplicationDetails })
    val children by map(2u, keyDefinition = StringDefinition(),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { CreationReplicationDetails }))
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

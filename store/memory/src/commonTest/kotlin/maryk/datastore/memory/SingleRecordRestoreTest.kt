package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdateHistory
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.datastore.shared.transaction
import maryk.datastore.shared.RestoreVersionRequest
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupReader
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.VersionedRestoreDataStore
import maryk.datastore.shared.restore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object UnindexedRestoreModel : RootDataModel<UnindexedRestoreModel>() {
    val title by string(1u, maxSize = 4u)
    val entries by list(2u, valueDefinition = StringDefinition(maxSize = 4u))
}

class SingleRecordRestoreTest {
    private fun key(index: Int) = Key<UnindexedRestoreModel>(ByteArray(16).also { it[15] = index.toByte() })

    private fun record(key: Key<UnindexedRestoreModel>, version: ULong, vararg changes: IsChange) =
        DataObjectVersionedChange(key, changes = listOf(VersionedChanges(version, changes.toList())))

    @Suppress("UNCHECKED_CAST")
    private fun request(vararg records: DataObjectVersionedChange<UnindexedRestoreModel>) = RestoreVersionRequest(listOf(
        UpdateResponse(UnindexedRestoreModel, InitialChangesUpdate(records.maxOf { it.changes.single().version } + 1uL, records.toList()))
            as UpdateResponse<IsRootDataModel>,
    ))

    private fun creation(key: Key<UnindexedRestoreModel>, version: ULong, entries: List<String> = List(64) { "one" }) =
        record(key, version, ObjectCreate, Change(UnindexedRestoreModel.title.ref() with "base", UnindexedRestoreModel.entries.ref() with entries))

    @Test
    fun singleRecordReplayPreservesHistoryIdempotenceAndDeletion() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to UnindexedRestoreModel), keepAllVersions = true, keepUpdateHistoryIndex = true)
        val key = key(1)
        try {
            store.restoreVersion(request(creation(key, 100uL)))
            val changed = request(record(key, 200uL, Change(UnindexedRestoreModel.title.ref() with "next")))
            store.restoreVersion(changed)
            store.restoreVersion(changed)
            val before = store.execute(UnindexedRestoreModel.get(key)).values.single()
            assertFailsWith<RequestException> {
                store.restoreVersion(request(record(key, 250uL,
                    Change(UnindexedRestoreModel.title.ref() with "fail", UnindexedRestoreModel.entries.ref() with listOf("oversized")),
                )))
            }
            assertEquals(before, store.execute(UnindexedRestoreModel.get(key)).values.single())
            assertEquals(listOf(200uL, 100uL), store.execute(UnindexedRestoreModel.getUpdateHistory(key)).updates.map { it.version })
            store.restoreVersion(request(record(key, 300uL, ObjectSoftDeleteChange(true))))
            assertTrue(store.execute(UnindexedRestoreModel.get(key)).values.isEmpty())
            store.restoreVersion(request(record(key, 400uL, ObjectSoftDeleteChange(false), Change(UnindexedRestoreModel.title.ref() with "back"))))
            val restored = store.execute(UnindexedRestoreModel.get(key)).values.single()
            assertEquals("back", restored.values { title })
            assertEquals(List(64) { "one" }, restored.values { entries })
            assertEquals(100uL, restored.firstVersion)
            assertEquals(400uL, restored.lastVersion)
            assertEquals(listOf(100uL, 200uL, 300uL, 400uL), store.execute(UnindexedRestoreModel.getChanges(key, maxVersions = 10u))
                .changes.single().changes.map { it.version })
        } finally { store.close() }
    }

    @Test
    fun restoreDetachesReaderBuffersAndKeepsVersionGroupsIndependent() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to UnindexedRestoreModel), keepAllVersions = true)
        val key = key(7)
        val entries = mutableListOf("one")
        val first = creation(key, 100uL, entries)
        val second = record(key, 200uL, Change(UnindexedRestoreModel.title.ref() with "next", UnindexedRestoreModel.entries.ref() with entries))
        val source = first.copy(changes = first.changes + second.changes)
        val name = UnindexedRestoreModel.Meta.name
        val reader = object : DataStoreBackupReader {
            override val manifest = DataStoreBackupManifest(snapshotVersion = 201uL, modelNames = listOf(name),
                modelMajorVersions = mapOf(name to UnindexedRestoreModel.Meta.version.major))
            override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) {
                consumer(DataStoreBackupChunk(name, listOf(source)))
                entries[0] = "two"
            }
        }
        val target = object : IsDataStore by store, VersionedRestoreDataStore by store {
            override suspend fun restoreVersion(request: RestoreVersionRequest) {
                store.restoreVersion(request)
                if (request.version == 100uL) {
                    val update = request.updates.single().update as InitialChangesUpdate
                    val pair = update.changes.single().changes.single().changes.filterIsInstance<Change>().single()
                        .referenceValuePairs.single { it.reference == UnindexedRestoreModel.entries.ref() }
                    @Suppress("UNCHECKED_CAST")
                    val ownedEntries = pair.value as MutableList<String>
                    ownedEntries[0] = "late"
                }
            }
        }
        try {
            assertEquals(1uL, target.restore(reader).records)
            for (version in listOf(100uL, 200uL)) {
                val restored = store.execute(UnindexedRestoreModel.get(key, toVersion = version)).values.single()
                assertEquals(listOf("one"), restored.values { this.entries })
            }
            assertEquals("next", store.execute(UnindexedRestoreModel.get(key)).values.single().values { title })
        } finally { store.close() }
    }

    @Test
    fun invalidCreationCannotPublishARecord() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to UnindexedRestoreModel), keepAllVersions = true, keepUpdateHistoryIndex = true)
        val key = key(2)
        try {
            assertFailsWith<RequestException> { store.restoreVersion(request(creation(key, 100uL, listOf("oversized")))) }
            assertTrue(store.execute(UnindexedRestoreModel.get(key)).values.isEmpty())
            assertTrue(store.execute(UnindexedRestoreModel.getUpdateHistory(key)).updates.isEmpty())
        } finally { store.close() }
    }

    @Test
    fun multipleRecordsStillRollBackAsOneVersionGroup() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to UnindexedRestoreModel), keepAllVersions = true, keepUpdateHistoryIndex = true)
        val first = key(3)
        val second = key(4)
        try {
            store.restoreVersion(request(creation(first, 100uL)))
            val changeFirst = record(first, 200uL, Change(UnindexedRestoreModel.title.ref() with "pair"))
            assertFailsWith<RequestException> { store.restoreVersion(request(changeFirst, creation(second, 200uL, listOf("oversized")))) }
            assertEquals("base", store.execute(UnindexedRestoreModel.get(first)).values.single().values { title })
            assertTrue(store.execute(UnindexedRestoreModel.get(second)).values.isEmpty())
            assertEquals(listOf(100uL), store.execute(UnindexedRestoreModel.getUpdateHistory(first)).updates.map { it.version })
            store.restoreVersion(request(changeFirst, creation(second, 200uL)))
            assertEquals("pair", store.execute(UnindexedRestoreModel.get(first)).values.single().values { title })
            assertEquals("base", store.execute(UnindexedRestoreModel.get(second)).values.single().values { title })
        } finally { store.close() }
    }

    @Test
    fun bothRestoreRoutesRejectAnActiveTransactionBeforeMutation() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to UnindexedRestoreModel), keepAllVersions = true)
        val key = key(5)
        val replay = request(creation(key, 100uL))
        try {
            for (direct in listOf(false, true)) {
                val error = assertFailsWith<IllegalStateException> {
                    store.transaction {
                        if (direct) store.execute(replay) else store.restoreVersion(replay)
                    }
                }
                assertEquals("Restore is not a transaction operation", error.message)
                assertTrue(store.execute(UnindexedRestoreModel.get(key)).values.isEmpty())
            }
            store.execute(replay)
            assertEquals("base", store.execute(UnindexedRestoreModel.get(key)).values.single().values { title })
        } finally { store.close() }
    }
}

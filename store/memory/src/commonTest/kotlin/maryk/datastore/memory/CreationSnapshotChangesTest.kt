package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.RootDataModel
import maryk.core.models.fromChanges
import maryk.core.models.graph
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.memory.processors.recordToObjectChanges
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DeletedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object CreationSnapshotModel : RootDataModel<CreationSnapshotModel>() {
    val title by string(1u)
    val entries by list(2u, valueDefinition = StringDefinition())
    val labels by map(3u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

class CreationSnapshotChangesTest {
    @Test
    fun creationSnapshotsPreserveProjectionBoundsAndOwnedCollections() = runTest {
        val store = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = mapOf(1u to CreationSnapshotModel))
        try {
            val values = CreationSnapshotModel.create {
                title with "before"
                entries with List(32) { "entry-$it" }
                labels with (0 until 32).associate { "label-$it" to "value-$it" }
            }
            val status = store.execute(CreationSnapshotModel.add(values)).statuses.single() as AddSuccess<CreationSnapshotModel>
            val creation = store.execute(CreationSnapshotModel.getChanges(status.key)).changes.single().changes.single()
            assertEquals(status.version, creation.version)
            assertEquals(values, CreationSnapshotModel.fromChanges(null, creation.changes))
            val mapPair = creation.changes.filterIsInstance<Change>().flatMap { it.referenceValuePairs }
                .single { it.reference == CreationSnapshotModel.labels.ref() }
            @Suppress("UNCHECKED_CAST")
            (mapPair.value as MutableMap<String, String>)["label-0"] = "caller mutation"
            assertEquals("value-0", store.execute(CreationSnapshotModel.get(status.key)).values.single().values { labels }?.get("label-0"))
            val selected = store.execute(CreationSnapshotModel.getChanges(status.key,
                select = CreationSnapshotModel.graph { listOf(title) })).changes.single().changes.single()
            val projection = CreationSnapshotModel.fromChanges(null, selected.changes)
            assertEquals("before", projection { title })
            assertNull(projection { entries })
            val empty = store.execute(CreationSnapshotModel.getChanges(status.key,
                select = CreationSnapshotModel.graph { emptyList() })).changes.single().changes.single()
            assertEquals(listOf(ObjectCreate), empty.changes)
            assertTrue(store.execute(CreationSnapshotModel.getChanges(status.key, fromVersion = status.version + 1uL)).changes.isEmpty())
            assertTrue(store.execute(CreationSnapshotModel.getChanges(status.key, toVersion = status.version - 1uL)).changes.isEmpty())
            store.execute(CreationSnapshotModel.change(status.key.change(Change(CreationSnapshotModel.title.ref() with "after"))))
            val historical = store.execute(CreationSnapshotModel.getChanges(status.key, toVersion = status.version,
                maxVersions = 10u)).changes.single().changes.single()
            assertEquals(values, CreationSnapshotModel.fromChanges(null, historical.changes))
        } finally {
            store.close()
        }
    }

    @Test
    fun staleRecordHeaderCannotHideNewerCells() {
        val nodes = ArrayList<DataRecordNode>()
        CreationSnapshotModel.create {
            title with "later"; entries with listOf("entry"); labels with mapOf("key" to "value")
        }.writeToStorage { _, qualifier, _, value ->
            val version = if (qualifier.contentEquals(CreationSnapshotModel.title.ref().toStorageByteArray())) 2uL else 1uL
            nodes += DataRecordValue(qualifier, value, HLC(version))
        }
        val record = DataRecord(Key<CreationSnapshotModel>(ByteArray(16)), nodes, HLC(1uL), HLC(1uL))
        assertEquals(HLC(2uL), record.currentVersion())
        val changes = CreationSnapshotModel.recordToObjectChanges(null, 0uL, null, 1u, null, record, true)
        assertEquals(setOf(1uL, 2uL), changes?.changes?.map { it.version }?.toSet())
        assertEquals(listOf(2uL), CreationSnapshotModel.recordToObjectChanges(null, 2uL, null, 1u, null, record, true)
            ?.changes?.map { it.version })
        assertNull(CreationSnapshotModel.recordToObjectChanges(null, 3uL, null, 1u, null, record, true))
    }

    @Test
    fun caughtUpReadsKeepNewerHistoryAndDeletionCellsDespiteStaleHeaders() {
        val qualifier = CreationSnapshotModel.title.ref().toStorageByteArray()
        val old = DataRecordValue(qualifier, "before", HLC(1uL))
        val deleted = DeletedValue<String>(qualifier, HLC(2uL))
        for (node in listOf<DataRecordNode>(
            DataRecordHistoricValues(qualifier, listOf(old, DataRecordValue(qualifier, "later", HLC(2uL)))),
            deleted,
            DataRecordHistoricValues(qualifier, listOf(old, deleted)),
        )) {
            val record = DataRecord(Key<CreationSnapshotModel>(ByteArray(16)), listOf(node), HLC(1uL), HLC(1uL))
            assertEquals(HLC(2uL), record.currentVersion())
            assertEquals(listOf(2uL), CreationSnapshotModel.recordToObjectChanges(null, 2uL, null, 1u, null, record, true)
                ?.changes?.map { it.version })
            assertNull(CreationSnapshotModel.recordToObjectChanges(null, 3uL, null, 1u, null, record, true))
        }
    }

    @Test
    fun recordVersionIncludesHeadersButExcludesUncommittedHistory() {
        val qualifier = CreationSnapshotModel.title.ref().toStorageByteArray()
        val node = DataRecordHistoricValues(qualifier, mutableListOf(DataRecordValue(qualifier, "before", HLC(1uL))))
        val record = DataRecord(Key<CreationSnapshotModel>(ByteArray(16)), listOf(node), HLC(1uL), HLC(2uL))
        node.add(DataRecordValue(qualifier, "after", HLC(3uL)))
        assertEquals(HLC(2uL), record.currentVersion())
        node.commit()
        assertEquals(HLC(3uL), record.currentVersion())
        record.values = emptyList()
        assertEquals(HLC(2uL), record.currentVersion())
    }
}

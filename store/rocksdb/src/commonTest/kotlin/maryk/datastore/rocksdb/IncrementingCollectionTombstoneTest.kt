package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.fromChanges
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import maryk.createTestDBFolder
import maryk.deleteFolder

private object AllocatedCollectionRecord : RootDataModel<AllocatedCollectionRecord>() {
    val entries by incrementingMap(1u, keyNumberDescriptor = UInt32,
        valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
}

class IncrementingCollectionTombstoneTest {
    @Test
    fun deletingAnEmptyAllocatedCollectionDoesNotExposeItAgain() = runTest {
        val folder = createTestDBFolder("allocated-collection-tombstone")
        suspend fun open() = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to AllocatedCollectionRecord), keepAllVersions = true)
        var store = open()
        try {
            val initial = AllocatedCollectionRecord.create { entries with mapOf(1u to emptyMap()) }
            val added = assertIs<AddSuccess<AllocatedCollectionRecord>>(store.execute(AllocatedCollectionRecord.add(initial)).statuses.single())
            val qualifier = AllocatedCollectionRecord.ref { entries at 1u }.toStorageByteArray()
            val columns = store.getColumnFamilies(AllocatedCollectionRecord)
            assertContentEquals(byteArrayOf(0), store.db.get(columns.table, added.key.bytes + qualifier)!!.copyOfRange(8, 9))
            val removed = assertIs<ChangeSuccess<AllocatedCollectionRecord>>(store.execute(AllocatedCollectionRecord.change(added.key.change(
                Change(AllocatedCollectionRecord.ref { entries at 1u } with null),
            ))).statuses.single())
            assertContentEquals(byteArrayOf(0, 0), store.db.get(columns.table, added.key.bytes + qualifier)!!.let { it.copyOfRange(8, it.size) })
            val deltas = store.execute(AllocatedCollectionRecord.getChanges(added.key,
                fromVersion = removed.version, toVersion = removed.version,
            )).changes.single().changes.flatMap { it.changes }
            assertEquals(emptyMap(), initial.change(deltas).get(AllocatedCollectionRecord.ref { entries }))
            store.close()
            store = open()
            assertEquals(emptyMap(), store.execute(AllocatedCollectionRecord.get(added.key)).values.single().values { entries })
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun historicalEmptyAllocatedCollectionRemainsPresent() = runTest {
        val folder = createTestDBFolder("allocated-collection-tombstone")
        suspend fun open() = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to AllocatedCollectionRecord), keepAllVersions = true)
        val store = open()
        try {
            val initial = AllocatedCollectionRecord.create { entries with mapOf(1u to emptyMap()) }
            val added = assertIs<AddSuccess<AllocatedCollectionRecord>>(store.execute(AllocatedCollectionRecord.add(initial)).statuses.single())
            assertEquals(initial, store.execute(AllocatedCollectionRecord.get(added.key, toVersion = added.version)).values.single().values)
            val changes = store.execute(AllocatedCollectionRecord.getChanges(added.key, toVersion = added.version)).changes.single().changes.flatMap { it.changes }
            assertEquals(initial, AllocatedCollectionRecord.fromChanges(null, changes))
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun appendUsesOnlyTheKeyBytesAfterComplexValuesAndDeletedEntries() = runTest {
        val folder = createTestDBFolder("allocated-collection-tombstone")
        suspend fun open() = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to AllocatedCollectionRecord), keepAllVersions = true)
        var store = open()
        try {
            val added = assertIs<AddSuccess<AllocatedCollectionRecord>>(store.execute(AllocatedCollectionRecord.add(
                AllocatedCollectionRecord.create { entries with mapOf(1u to emptyMap()) },
            )).statuses.single())
            assertIs<ChangeSuccess<AllocatedCollectionRecord>>(store.execute(AllocatedCollectionRecord.change(added.key.change(
                IncMapChange(AllocatedCollectionRecord.ref { entries }.change(addValues = listOf(mapOf("nested" to "value")))),
            ))).statuses.single())
            assertEquals(mapOf(1u to emptyMap(), 2u to mapOf("nested" to "value")), store.execute(AllocatedCollectionRecord.get(added.key)).values.single().values { entries })
            assertIs<ChangeSuccess<AllocatedCollectionRecord>>(store.execute(AllocatedCollectionRecord.change(added.key.change(
                Change(AllocatedCollectionRecord.ref { entries at 2u } with null),
            ))).statuses.single())
            assertIs<ChangeSuccess<AllocatedCollectionRecord>>(store.execute(AllocatedCollectionRecord.change(added.key.change(
                IncMapChange(AllocatedCollectionRecord.ref { entries }.change(addValues = listOf(emptyMap<String, String>()))),
            ))).statuses.single())
            store.close()
            store = open()
            assertEquals(mapOf(1u to emptyMap(), 3u to emptyMap()), store.execute(AllocatedCollectionRecord.get(added.key)).values.single().values { entries })
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

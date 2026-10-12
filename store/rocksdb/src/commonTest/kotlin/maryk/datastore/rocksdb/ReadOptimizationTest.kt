package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.models.key
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.shared.beginTransaction
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import maryk.createTestDBFolder

private object ReadOptimizationModel : RootDataModel<ReadOptimizationModel>() {
    val label by string(index = 1u)
    val note by string(index = 2u, required = false)
    val entries by map(index = 3u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

class ReadOptimizationTest {
    @Test
    fun batchedReadsPreserveOrderDuplicatesMissingAndSelection() = runTest {
        val store = RocksDBDataStore.open(relativePath = createTestDBFolder("read-optimization"), dataModelsById = mapOf(1u to ReadOptimizationModel), keepAllVersions = true)
        try {
            val keys = store.execute(ReadOptimizationModel.add(*Array(265) { index ->
                ReadOptimizationModel.create { label with "record-$index"; entries with mapOf("child" to "$index") }
            })).statuses.map { assertIs<AddSuccess<ReadOptimizationModel>>(it).key }
            val missing = Key<ReadOptimizationModel>(keys.first().bytes.copyOf().apply { this[0] = (this[0].toInt() xor 127).toByte() })
            val requested = keys.reversed() + listOf(missing, keys.first(), keys.last())
            val result = store.execute(ReadOptimizationModel.get(*requested.toTypedArray(), select = ReadOptimizationModel.graph { listOf(label) }))
            assertEquals(keys.reversed() + listOf(keys.first(), keys.last()), result.values.map { it.key })
            result.values.forEach { assertNull(it.values[ReadOptimizationModel.entries.ref()]) }
        } finally { store.close() }
    }

    @Test
    fun batchedReadsSeeEarlierTransactionAdditions() = runTest {
        val store = RocksDBDataStore.open(relativePath = createTestDBFolder("read-optimization"), dataModelsById = mapOf(1u to ReadOptimizationModel), keepAllVersions = true)
        try {
            val objects = Array(10) { index -> ReadOptimizationModel.create {
                label with "staged-$index"; entries with mapOf("child" to "$index")
            } }
            val keys = objects.map { ReadOptimizationModel.key(it) }
            val transaction = store.beginTransaction()
            transaction.execute(ReadOptimizationModel.add(*keys.zip(objects.toList()).toTypedArray()))
            val read = transaction.execute(ReadOptimizationModel.get(*keys.reversed().toTypedArray(),
                select = ReadOptimizationModel.graph { listOf(label) })).response
            assertEquals(keys.reversed(), read.values.map { it.key })
            assertEquals(TransactionStatus.Committed, transaction.commit().status)
        } finally { store.close() }
    }

    @Test
    fun historicalReadsKeepVisibleDeletionsAndCollectionChildren() = runTest {
        val store = RocksDBDataStore.open(relativePath = createTestDBFolder("read-optimization"), dataModelsById = mapOf(1u to ReadOptimizationModel), keepAllVersions = true)
        try {
            val add = assertIs<AddSuccess<ReadOptimizationModel>>(store.execute(ReadOptimizationModel.add(
                ReadOptimizationModel.create { label with "first"; note with "visible"; entries with mapOf("a" to "old", "b" to "retained") }
            )).statuses.single())
            val selectedNote = store.execute(ReadOptimizationModel.get(add.key,
                select = ReadOptimizationModel.graph { listOf(note) })).values.single()
            assertEquals("visible", selectedNote.values[ReadOptimizationModel.note.ref()])
            assertNull(selectedNote.values[ReadOptimizationModel.label.ref()])
            assertNull(selectedNote.values[ReadOptimizationModel.entries.ref()])
            val changed = assertIs<ChangeSuccess<ReadOptimizationModel>>(store.execute(ReadOptimizationModel.change(add.key.change(Change(
                ReadOptimizationModel.ref { label } with "second",
                ReadOptimizationModel.ref { note } with null,
                ReadOptimizationModel.ref { entries } with mapOf("b" to "retained", "c" to "new"),
            )))).statuses.single())
            repeat(20) { index -> store.execute(ReadOptimizationModel.change(add.key.change(Change(ReadOptimizationModel.ref { label } with "later-$index")))) }
            val old = store.execute(ReadOptimizationModel.get(add.key, toVersion = add.version)).values.single()
            assertEquals("first", old.values[ReadOptimizationModel.label.ref()])
            assertEquals(mapOf("a" to "old", "b" to "retained"), old.values[ReadOptimizationModel.entries.ref()])
            val atChange = store.execute(ReadOptimizationModel.get(add.key, toVersion = changed.version)).values.single()
            assertEquals("second", atChange.values[ReadOptimizationModel.label.ref()])
            assertNull(atChange.values[ReadOptimizationModel.note.ref()])
            assertEquals(mapOf("b" to "retained", "c" to "new"), atChange.values[ReadOptimizationModel.entries.ref()])
            val projected = store.execute(ReadOptimizationModel.get(add.key, toVersion = changed.version,
                select = ReadOptimizationModel.graph { listOf(label, note) })).values.single()
            assertEquals("second", projected.values[ReadOptimizationModel.label.ref()])
            assertNull(projected.values[ReadOptimizationModel.note.ref()])
            assertNull(projected.values[ReadOptimizationModel.entries.ref()])
            assertEquals(changed.version, projected.lastVersion)
        } finally { store.close() }
    }
}

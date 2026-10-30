package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.memory.processors.processChange
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DataStore
import maryk.lib.extensions.compare.compareTo
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.MarykTypeEnum.T4
import maryk.test.models.MarykTypeEnum.T5
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TypedMutationIntegrityTest {
    @Test
    fun checksCannotReadInactiveRetainedChildrenOrPublishEarlierWrites() = runTest {
        for (history in listOf(false, true)) {
            val original = ComplexModel.create(setDefaults = false) {
                multi with T3(EmbeddedMarykModel.create(setDefaults = false) { value with "retained" })
            }
            val root = ComplexModel.ref { multi }.toStorageByteArray()
            val rows = mutableListOf<DataRecordValue<Any>>()
            original.writeToStorage { _, qualifier, _, value -> rows += DataRecordValue(qualifier, value, HLC(1uL)) }
            val currentRoot = DataRecordValue<Any>(root, T1("active"), HLC(2uL))
            val rootNode = if (history) DataRecordHistoricValues(root,
                listOf(rows.first { it.reference.contentEquals(root) }, currentRoot)) else currentRoot
            val retainedRows: List<DataRecordNode> = (rows.filterNot { it.reference.contentEquals(root) } + rootNode)
                .sortedWith { first, second -> first.reference.compareTo(second.reference) }
            val key = Key<ComplexModel>(ByteArray(ComplexModel.Meta.keyByteSize))
            val record = DataRecord(key, retainedRows, HLC(1uL), HLC(2uL))
            val store = DataStore(history, false, ComplexModel).apply { records += record }
            val child = EmbeddedMarykModel.ref(ComplexModel.ref { multi.atType(T3) }) { value }
            val status = processChange(store, ComplexModel, key, null, listOf(
                Change(ComplexModel.ref { mapStringString } with mapOf("staged" to "must roll back")),
                Check(child with "retained"),
            ), HLC(3uL), {})
            assertIs<ValidationFail<ComplexModel>>(status)
            assertEquals(HLC(2uL), record.lastVersion)
            assertEquals(retainedRows, record.values)
        }
    }

    @Test
    fun inactiveAndMalformedTypedWritesCannotPublishOrBreakHistory() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = history)
            try {
                val original = ComplexModel.create(setDefaults = false) { multi with T1("active") }
                val added = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(original)).statuses.single())
                val typedEmbed = ComplexModel.ref { multi.atType(T3) }
                val embeddedChild = EmbeddedMarykModel.ref(typedEmbed) { value }
                val nestedModel = EmbeddedMarykModel.ref(typedEmbed) { marykModel }
                val nestedAllocation = TestMarykModel.ref(nestedModel) { incMap }
                @Suppress("UNCHECKED_CAST")
                val inactiveList = ComplexModel.ref { multi.atType(T4) } as IsPropertyReference<List<String>, IsListDefinition<String, *>, *>
                @Suppress("UNCHECKED_CAST")
                val inactiveSet = ComplexModel.ref { multi.atType(T5) } as IsPropertyReference<Set<String>, IsPropertyDefinition<Set<String>>, *>
                val invalid = listOf(
                    Change(ComplexModel.ref { multi.atType(T2) } with 3),
                    Change(ComplexModel.ref { multi.atType(T1) } with "illegal extra simple qualifier"),
                    Change(embeddedChild with "inactive child"),
                    ListChange(inactiveList.change(addValuesToEnd = listOf("inactive"))),
                    SetChange(inactiveSet.change(addValues = setOf("inactive"))),
                    IncMapChange(nestedAllocation.change(addValues = listOf("inactive"))),
                )
                for (operation in invalid) {
                    val status = store.execute(ComplexModel.change(added.key.change(
                        Change(ComplexModel.ref { mapStringString } with mapOf("staged" to "must roll back")), operation,
                    ))).statuses.single()
                    assertIs<ServerFail<ComplexModel>>(status, operation.toString())
                    val current = store.execute(ComplexModel.get(added.key)).values.single()
                    assertEquals(original, current.values)
                    assertEquals(added.version, current.lastVersion)
                    store.execute(ComplexModel.getChanges(added.key))
                }
                for (operation in listOf(Change(typedEmbed with null), Change(embeddedChild with null))) {
                    val status = assertIs<ChangeSuccess<ComplexModel>>(store.execute(ComplexModel.change(
                        added.key.change(operation),
                    )).statuses.single())
                    assertEquals(added.version, status.version)
                    assertEquals(original, store.execute(ComplexModel.get(added.key)).values.single().values)
                    store.execute(ComplexModel.getChanges(added.key))
                }
            } finally { store.close() }
        }
    }

    @Test
    fun precedingParentActivationAllowsItsChildEditAndRetainsHistory() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = true)
        try {
            val original = ComplexModel.create(setDefaults = false) { multi with T1("active") }
            val added = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(original)).statuses.single())
            val supplied = EmbeddedMarykModel.create(setDefaults = false) { value with "supplied" }
            val child = EmbeddedMarykModel.ref(ComplexModel.ref { multi.atType(T3) }) { value }
            val response = store.execute(ComplexModel.change(added.key.change(
                Change(ComplexModel.ref { multi } with T3(supplied), child with "edited"),
            ))).statuses.single()
            val changed = assertIs<ChangeSuccess<ComplexModel>>(response, response.toString())
            assertTrue(changed.version > added.version)
            val expected = ComplexModel.create(setDefaults = false) {
                multi with T3(EmbeddedMarykModel.create(setDefaults = false) { value with "edited" })
            }
            assertEquals(expected, store.execute(ComplexModel.get(added.key)).values.single().values)
            assertEquals(original, store.execute(ComplexModel.get(added.key, toVersion = added.version)).values.single().values)
            assertEquals(expected, store.execute(ComplexModel.get(added.key, toVersion = changed.version)).values.single().values)
            assertEquals("supplied", supplied { value })
            store.execute(ComplexModel.getChanges(added.key))
        } finally { store.close() }
    }
}

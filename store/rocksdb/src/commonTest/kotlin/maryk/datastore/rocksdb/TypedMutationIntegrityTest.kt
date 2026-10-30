package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.lib.extensions.compare.matchesRangePart
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Check
import maryk.core.query.changes.Change
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
    fun inactiveAndMalformedTypedWritesCannotPublishOrBreakHistory() = runTest {
        for (history in listOf(false, true)) {
            val folder = createTestDBFolder("typed-mutation-$history")
            val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = history)
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
            } finally { store.close(); deleteFolder(folder) }
        }
    }

    @Test
    fun precedingParentActivationAllowsItsChildEditAndRetainsHistory() = runTest {
        val folder = createTestDBFolder("typed-mutation-activation")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = true)
        try {
            val original = ComplexModel.create(setDefaults = false) { multi with T1("active") }
            val added = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(original)).statuses.single())
            val supplied = EmbeddedMarykModel.create(setDefaults = false) { value with "supplied" }
            val child = EmbeddedMarykModel.ref(ComplexModel.ref { multi.atType(T3) }) { value }
            val changed = assertIs<ChangeSuccess<ComplexModel>>(store.execute(ComplexModel.change(added.key.change(
                Change(ComplexModel.ref { multi } with T3(supplied), child with "edited"),
            ))).statuses.single())
            assertTrue(changed.version > added.version)
            val expected = ComplexModel.create(setDefaults = false) {
                multi with T3(EmbeddedMarykModel.create(setDefaults = false) { value with "edited" })
            }
            assertEquals(expected, store.execute(ComplexModel.get(added.key)).values.single().values)
            assertEquals(original, store.execute(ComplexModel.get(added.key, toVersion = added.version)).values.single().values)
            assertEquals(expected, store.execute(ComplexModel.get(added.key, toVersion = changed.version)).values.single().values)
            assertEquals("supplied", supplied { value })
            store.execute(ComplexModel.getChanges(added.key))
        } finally { store.close(); deleteFolder(folder) }
    }

    @Test
    fun retainedInactiveChildCellsCannotBeDeletedOrOverwritten() = runTest() {
        val folder = createTestDBFolder("typed-retained-mutation")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = true)
        try {
            val original = ComplexModel.create(setDefaults = false) {
                multi with T3(EmbeddedMarykModel.create(setDefaults = false) { value with "retained" })
            }
            val added = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(original)).statuses.single())
            val families = store.getColumnFamilies(1u)
            val prefix = added.key.bytes + ComplexModel.ref { multi }.toStorageByteArray()
            fun rows() = buildList<Pair<ByteArray, ByteArray>> {
                store.db.newIterator(families.table, store.defaultReadOptions).use { iterator ->
                    iterator.seek(prefix)
                    while (iterator.isValid() && iterator.key().matchesRangePart(0, prefix)) {
                        add(iterator.key() to iterator.value())
                        iterator.next()
                    }
                }
            }
            val retained = rows().filter { it.first.size > prefix.size }
            val changed = assertIs<ChangeSuccess<ComplexModel>>(store.execute(ComplexModel.change(added.key.change(
                Change(ComplexModel.ref { multi } with T1("active")),
            ))).statuses.single())
            for ((row, value) in retained) store.db.put(families.table, row, value)
            val before = rows().map { it.first.toHexString() to it.second.toHexString() }
            val typedEmbed = ComplexModel.ref { multi.atType(T3) }
            val child = EmbeddedMarykModel.ref(typedEmbed) { value }
            assertIs<ValidationFail<ComplexModel>>(store.execute(ComplexModel.change(added.key.change(Check(child with "retained")))).statuses.single())
            for (operation in listOf(Change(typedEmbed with null), Change(child with null))) {
                val status = assertIs<ChangeSuccess<ComplexModel>>(store.execute(ComplexModel.change(added.key.change(operation))).statuses.single())
                assertEquals(changed.version, status.version)
            }
            assertIs<ServerFail<ComplexModel>>(store.execute(ComplexModel.change(added.key.change(Change(child with "inactive")))).statuses.single())
            assertEquals(before, rows().map { it.first.toHexString() to it.second.toHexString() })
            val current = store.execute(ComplexModel.get(added.key)).values.single()
            assertEquals(changed.version, current.lastVersion)
            assertEquals(T1("active"), current.values { multi })
            assertEquals(original, store.execute(ComplexModel.get(added.key, toVersion = added.version)).values.single().values)
            store.execute(ComplexModel.getChanges(added.key))
        } finally { store.close(); deleteFolder(folder) }
    }
}

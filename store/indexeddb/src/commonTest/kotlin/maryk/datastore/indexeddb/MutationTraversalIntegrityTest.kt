package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.MarykTypeEnum.T3
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MutationTraversalIntegrityTest {
    @Test
    fun nestedWildcardMutationRetainsCompleteItemsThroughHistoryAndReopen() = runTest {
        installIndexedDbForTests()
        for (history in listOf(false, true)) {
            val name = "wildcard-mutation-$history-${Random.nextInt()}"
            suspend fun open() = IndexedDbDataStore.open(name, mapOf(1u to ComplexModel), keepAllVersions = history)
            var store = open()
            try {
                fun embedded(label: String) = EmbeddedMarykModel.create(setDefaults = false) {
                    value with label
                    model with EmbeddedMarykModel.create(setDefaults = false) { value with "kept" }
                }
                val original = ComplexModel.create(setDefaults = false) {
                    mapIntObject with linkedMapOf(1u to embedded("one"), 2u to embedded("two"), 3u to embedded("three"))
                }
                val added = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(original)).statuses.single())
                val child = EmbeddedMarykModel.ref(ComplexModel.ref { mapIntObject.anyValue() }) { value }
                val changed = assertIs<ChangeSuccess<ComplexModel>>(store.execute(ComplexModel.change(
                    added.key.change(Change(child with "changed")),
                )).statuses.single())
                assertTrue(changed.version > added.version)
                val expected = ComplexModel.create(setDefaults = false) {
                    mapIntObject with linkedMapOf(1u to embedded("changed"), 2u to embedded("changed"), 3u to embedded("changed"))
                }
                assertEquals(expected, store.execute(ComplexModel.get(added.key)).values.single().values)
                store.close()
                store = open()
                assertEquals(expected, store.execute(ComplexModel.get(added.key)).values.single().values)
                if (history) {
                    assertEquals(original, store.execute(ComplexModel.get(added.key, toVersion = added.version)).values.single().values)
                    assertEquals(expected, store.execute(ComplexModel.get(added.key, toVersion = changed.version)).values.single().values)
                }
            } finally { store.close() }
        }
    }

    @Test
    fun inactiveTypedMutationCannotEraseTheActiveCaseOrPublishEarlierChanges() = runTest {
        installIndexedDbForTests()
        for (history in listOf(false, true)) {
            val name = "inactive-type-mutation-$history-${Random.nextInt()}"
            suspend fun open() = IndexedDbDataStore.open(name, mapOf(1u to ComplexModel), keepAllVersions = history)
            var store = open()
            try {
                val original = ComplexModel.create(setDefaults = false) { multi with T1("active") }
                val added = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(original)).statuses.single())
                val deletion = assertIs<ChangeSuccess<ComplexModel>>(store.execute(ComplexModel.change(
                    added.key.change(Change(ComplexModel.ref { multi.atType(T3) } with null)),
                )).statuses.single())
                assertEquals(added.version, deletion.version)
                assertEquals(original, store.execute(ComplexModel.get(added.key)).values.single().values)

                val rejected = store.execute(ComplexModel.change(added.key.change(
                    Change(ComplexModel.ref { mapStringString } with mapOf("staged" to "must roll back")),
                    Change(ComplexModel.ref { multi.atType(T2) } with 3),
                ))).statuses.single()
                assertIs<ServerFail<ComplexModel>>(rejected)
                val unchanged = store.execute(ComplexModel.get(added.key)).values.single()
                assertEquals(original, unchanged.values)
                assertEquals(added.version, unchanged.lastVersion)
                store.close()
                store = open()
                assertEquals(original, store.execute(ComplexModel.get(added.key)).values.single().values)
                if (history) assertEquals(original, store.execute(ComplexModel.get(added.key, toVersion = added.version)).values.single().values)
            } finally { store.close() }
        }
    }
}

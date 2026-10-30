package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ValidationFail
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object OrderedMutationValues : RootDataModel<OrderedMutationValues>() {
    val token by string(1u, unique = true)
    val allocated by incrementingMap(2u, required = false, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
    val items by list(3u, required = false, valueDefinition = StringDefinition())
    val attributes by map(4u, required = false, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

class OrderedMutationMaterializationTest {
    @Test
    fun collectionCreationAndFollowingEditsShareOneAtomicResult() = runTest {
        installIndexedDbForTests()
        for (history in listOf(false, true)) {
            val name = "ordered-mutation-$history-${Random.nextInt()}"
            suspend fun open() = IndexedDbDataStore.open(name, mapOf(1u to OrderedMutationValues), keepAllVersions = history)
            var store = open()
            try {
                val initial = OrderedMutationValues.create { token with "subject" }
                val added = assertIs<AddSuccess<OrderedMutationValues>>(store.execute(OrderedMutationValues.add(initial)).statuses.single())
                assertIs<AddSuccess<OrderedMutationValues>>(store.execute(OrderedMutationValues.add(OrderedMutationValues.create { token with "reserved" })).statuses.single())
                val rejected = store.execute(OrderedMutationValues.change(added.key.change(
                    Change(OrderedMutationValues.ref { allocated } with emptyMap<UInt, String>()),
                    IncMapChange(OrderedMutationValues.ref { allocated }.change(addValues = listOf("must roll back"))),
                    Change(OrderedMutationValues.ref { token } with "reserved"),
                ))).statuses.single()
                assertIs<ValidationFail<OrderedMutationValues>>(rejected, rejected.toString())
                val unchanged = store.execute(OrderedMutationValues.get(added.key)).values.single()
                assertEquals(initial, unchanged.values)
                assertEquals(added.version, unchanged.lastVersion)

                val accepted = store.execute(OrderedMutationValues.change(added.key.change(
                    Change(OrderedMutationValues.ref { allocated } with emptyMap<UInt, String>()),
                    IncMapChange(OrderedMutationValues.ref { allocated }.change(addValues = listOf("first"))),
                    Change(OrderedMutationValues.ref { items } with emptyList<String>()),
                    ListChange(OrderedMutationValues.ref { items }.change(addValuesToEnd = listOf("appended"))),
                    Change(OrderedMutationValues.ref { attributes } with emptyMap<String, String>(),
                        OrderedMutationValues.ref { attributes at "child" } with "created after parent"),
                ))).statuses.single()
                val changed = assertIs<ChangeSuccess<OrderedMutationValues>>(accepted, accepted.toString())
                val expected = OrderedMutationValues.create {
                    token with "subject"
                    allocated with mapOf(1u to "first")
                    items with listOf("appended")
                    attributes with mapOf("child" to "created after parent")
                }
                assertEquals(expected, store.execute(OrderedMutationValues.get(added.key)).values.single().values)
                store.close()
                store = open()
                assertEquals(expected, store.execute(OrderedMutationValues.get(added.key)).values.single().values)
                if (history) {
                    assertEquals(initial, store.execute(OrderedMutationValues.get(added.key, toVersion = added.version)).values.single().values)
                    assertEquals(expected, store.execute(OrderedMutationValues.get(added.key, toVersion = changed.version)).values.single().values)
                }
            } finally {
                store.close()
            }
        }
    }
}

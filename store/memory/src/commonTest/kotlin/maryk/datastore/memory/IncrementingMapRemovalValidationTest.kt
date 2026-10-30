package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ValidationFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object RemovalAllocatedRecord : RootDataModel<RemovalAllocatedRecord>() {
    val optional by incrementingMap(1u, required = false, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
    val required by incrementingMap(2u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
    val locked by list(3u, final = true, valueDefinition = StringDefinition())
    val bounded by list(4u, minSize = 2u, valueDefinition = StringDefinition())
}

class IncrementingMapRemovalValidationTest {
    @Test
    fun optionalRemovalKeepsMaximumWhileRequiredRemovalRollsBack() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to RemovalAllocatedRecord), keepAllVersions = history)
            try {
                val initial = RemovalAllocatedRecord.create {
                    optional with mapOf(25u to "old"); required with mapOf(1u to "kept")
                    locked with listOf("first", "second"); bounded with listOf("first", "second")
                }
                val added = assertIs<AddSuccess<RemovalAllocatedRecord>>(store.execute(RemovalAllocatedRecord.add(initial)).statuses.single())
                val removed = assertIs<ChangeSuccess<RemovalAllocatedRecord>>(store.execute(RemovalAllocatedRecord.change(added.key.change(
                    Change(RemovalAllocatedRecord.ref { optional } with null),
                ))).statuses.single())
                val afterRemoval = RemovalAllocatedRecord.create {
                    required with mapOf(1u to "kept")
                    locked with listOf("first", "second"); bounded with listOf("first", "second")
                }
                assertEquals(afterRemoval, store.execute(RemovalAllocatedRecord.get(added.key)).values.single().values)
                assertIs<ValidationFail<RemovalAllocatedRecord>>(store.execute(RemovalAllocatedRecord.change(added.key.change(
                    Change(RemovalAllocatedRecord.ref { required } with null),
                ))).statuses.single())
                val unchanged = store.execute(RemovalAllocatedRecord.get(added.key)).values.single()
                assertEquals(afterRemoval, unchanged.values)
                assertEquals(removed.version, unchanged.lastVersion)
                for (reference in listOf(RemovalAllocatedRecord.ref { locked at 0u }, RemovalAllocatedRecord.ref { bounded at 0u })) {
                    assertIs<ValidationFail<RemovalAllocatedRecord>>(store.execute(RemovalAllocatedRecord.change(added.key.change(
                        Change(RemovalAllocatedRecord.ref { optional } with mapOf(99u to "must roll back")),
                        Change(reference with null),
                    ))).statuses.single())
                    val afterRejectedRemoval = store.execute(RemovalAllocatedRecord.get(added.key)).values.single()
                    assertEquals(afterRemoval, afterRejectedRemoval.values)
                    assertEquals(removed.version, afterRejectedRemoval.lastVersion)
                }
                assertIs<ChangeSuccess<RemovalAllocatedRecord>>(store.execute(RemovalAllocatedRecord.change(added.key.change(
                    Change(RemovalAllocatedRecord.ref { optional } with emptyMap<UInt, String>()),
                    IncMapChange(RemovalAllocatedRecord.ref { optional }.change(addValues = listOf("next"))),
                ))).statuses.single())
                assertEquals(mapOf(26u to "next"), store.execute(RemovalAllocatedRecord.get(added.key)).values.single().values { optional })
                if (history) {
                    assertEquals(initial, store.execute(RemovalAllocatedRecord.get(added.key, toVersion = added.version)).values.single().values)
                    assertEquals(afterRemoval, store.execute(RemovalAllocatedRecord.get(added.key, toVersion = removed.version)).values.single().values)
                }
            } finally {
                store.close()
            }
        }
    }
}

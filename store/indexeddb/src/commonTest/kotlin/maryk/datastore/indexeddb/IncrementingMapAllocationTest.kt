package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.types.numeric.UInt8
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
import maryk.core.query.responses.statuses.ServerFail
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object AllocatedValues : RootDataModel<AllocatedValues>() {
    val scalar by incrementingMap(1u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
    val collections by incrementingMap(2u, keyNumberDescriptor = UInt32,
        valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
}

private object SmallAllocatedValues : RootDataModel<SmallAllocatedValues>() {
    val scalar by incrementingMap(1u, keyNumberDescriptor = UInt8, valueDefinition = StringDefinition())
}

class IncrementingMapAllocationTest {
    @Test
    fun deletedAndReplacedEntriesDoNotResetAllocationAfterReopen() = runTest {
        installIndexedDbForTests()
        for (history in listOf(false, true)) {
            val name = "maryk-incmap-watermark-$history-${Random.nextInt()}"
            suspend fun open() = IndexedDbDataStore.open(name, mapOf(1u to AllocatedValues), keepAllVersions = history)
            var store = open()
            try {
                val added = assertIs<AddSuccess<AllocatedValues>>(store.execute(AllocatedValues.add(
                    AllocatedValues.create {
                        scalar with mapOf(1u to "first", 2u to "deleted maximum")
                        collections with mapOf(1u to emptyMap(), 2u to emptyMap())
                    },
                )).statuses.single())
                assertIs<ChangeSuccess<AllocatedValues>>(store.execute(AllocatedValues.change(added.key.change(Change(
                    AllocatedValues.ref { scalar at 2u } with null,
                    AllocatedValues.ref { collections at 2u } with null,
                )))).statuses.single())
                store.close()
                store = open()
                assertIs<ChangeSuccess<AllocatedValues>>(store.execute(AllocatedValues.change(added.key.change(IncMapChange(
                    AllocatedValues.ref { scalar }.change(addValues = listOf("third")),
                    AllocatedValues.ref { collections }.change(addValues = listOf(emptyMap<String, String>())),
                )))).statuses.single())
                val afterAppend = store.execute(AllocatedValues.get(added.key)).values.single()
                assertEquals(mapOf(1u to "first", 3u to "third"), afterAppend.values { scalar })
                assertEquals(mapOf(1u to emptyMap(), 3u to emptyMap()), afterAppend.values { collections })
                assertIs<ChangeSuccess<AllocatedValues>>(store.execute(AllocatedValues.change(added.key.change(
                    Change(AllocatedValues.ref { scalar at 50u } with "manual"),
                    IncMapChange(AllocatedValues.ref { scalar }.change(addValues = listOf("after manual"))),
                ))).statuses.single())
                assertEquals("after manual", store.execute(AllocatedValues.get(added.key)).values.single().values { scalar }?.get(51u))
                assertIs<ChangeSuccess<AllocatedValues>>(store.execute(AllocatedValues.change(added.key.change(Change(
                    AllocatedValues.ref { scalar } with emptyMap<UInt, String>(),
                    AllocatedValues.ref { collections } with emptyMap<UInt, Map<String, String>>(),
                )))).statuses.single())
                store.close()
                store = open()
                assertIs<ChangeSuccess<AllocatedValues>>(store.execute(AllocatedValues.change(added.key.change(IncMapChange(
                    AllocatedValues.ref { scalar }.change(addValues = listOf("next")),
                    AllocatedValues.ref { collections }.change(addValues = listOf(emptyMap<String, String>())),
                )))).statuses.single())
                val final = store.execute(AllocatedValues.get(added.key)).values.single()
                assertEquals(mapOf(52u to "next"), final.values { scalar })
                assertEquals(mapOf(4u to emptyMap()), final.values { collections })
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun allocationExhaustionDoesNotWrapIntoAnOlderKey() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("maryk-incmap-overflow-${Random.nextInt()}", mapOf(1u to SmallAllocatedValues))
        try {
            val added = assertIs<AddSuccess<SmallAllocatedValues>>(store.execute(SmallAllocatedValues.add(
                SmallAllocatedValues.create { scalar with mapOf(255.toUByte() to "last") },
            )).statuses.single())
            assertIs<ServerFail<SmallAllocatedValues>>(store.execute(SmallAllocatedValues.change(added.key.change(
                IncMapChange(SmallAllocatedValues.ref { scalar }.change(addValues = listOf("overflow"))),
            ))).statuses.single())
            val current = store.execute(SmallAllocatedValues.get(added.key)).values.single()
            assertEquals(mapOf(255.toUByte() to "last"), current.values { scalar })
            assertEquals(added.version, current.lastVersion)
        } finally {
            store.close()
        }
    }
}

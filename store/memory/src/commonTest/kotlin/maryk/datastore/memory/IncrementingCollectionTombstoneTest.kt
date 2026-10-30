package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
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
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object AllocatedCollectionRecord : RootDataModel<AllocatedCollectionRecord>() {
    val entries by incrementingMap(1u, keyNumberDescriptor = UInt32,
        valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
}


class IncrementingCollectionTombstoneTest {

    @Test
    fun appendUsesOnlyTheKeyBytesAfterComplexValuesAndDeletedEntries() = runTest {
        suspend fun open() = InMemoryDataStore.open(dataModelsById = mapOf(1u to AllocatedCollectionRecord), keepAllVersions = true)
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

            assertEquals(mapOf(1u to emptyMap(), 3u to emptyMap()), store.execute(AllocatedCollectionRecord.get(added.key)).values.single().values { entries })
        } finally {
            store.close()

        }
    }
}

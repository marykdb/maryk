package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import kotlin.random.Random
import kotlin.test.assertContentEquals

private object NestedCountRecord : RootDataModel<NestedCountRecord>() {
    val mappings by map(1u, required = false, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()), maxSize = 2u)
    val lists by map(2u, required = false, keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()), maxSize = 2u)
    val sets by map(3u, required = false, keyDefinition = StringDefinition(), valueDefinition = SetDefinition(valueDefinition = StringDefinition()), maxSize = 2u)
    val scalar by map(4u, required = false, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
    val marker by string(5u, required = false)
}

class NestedCollectionCountTest {
    @Test
    fun replacementsAndEmptyEntryLifecycleMaintainParentCounts() = runTest {
        for (history in listOf(false, true)) {
            val directory = listOf("maryk", "nested-collection-count-$history-${Random.nextInt()}")
            suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
                dataModelsById = mapOf(1u to NestedCountRecord), keepAllVersions = history)
            var store = open()
            try {
                val initial = NestedCountRecord.create {
                    mappings with mapOf("old" to emptyMap())
                    lists with mapOf("old" to emptyList())
                    sets with mapOf("old" to emptySet())
                    scalar with emptyMap()
                }
                val added = assertIs<AddSuccess<NestedCountRecord>>(store.execute(NestedCountRecord.add(initial)).statuses.single())
                assertIs<ChangeSuccess<NestedCountRecord>>(store.execute(NestedCountRecord.change(added.key.change(Change(
                    NestedCountRecord.ref { mappings at "old" } with emptyMap<String, String>(),
                    NestedCountRecord.ref { lists at "old" } with emptyList<String>(),
                    NestedCountRecord.ref { sets at "old" } with emptySet<String>(),
                    NestedCountRecord.ref { scalar at "first" } with "present empty parent",
                    NestedCountRecord.ref { marker } with "publish no-op replacements",
                )))).statuses.single())
                val dirsBefore = store.getTableDirs(NestedCountRecord)
                store.runTransaction { tr ->
                    for (reference in listOf(NestedCountRecord.ref { mappings }, NestedCountRecord.ref { lists }, NestedCountRecord.ref { sets })) assertContentEquals(byteArrayOf(1), tr.get(packKey(dirsBefore.tablePrefix, added.key.bytes, reference.toStorageByteArray())).awaitResult()!!.copyOfRange(8, 9))
                }
                assertIs<ChangeSuccess<NestedCountRecord>>(store.execute(NestedCountRecord.change(added.key.change(Change(
                    NestedCountRecord.ref { mappings at "old" } with mapOf("nested" to "value"),
                    NestedCountRecord.ref { lists at "old" } with listOf("value"),
                    NestedCountRecord.ref { sets at "old" } with setOf("value"),
                )))).statuses.single())
                assertIs<ChangeSuccess<NestedCountRecord>>(store.execute(NestedCountRecord.change(added.key.change(Change(
                    NestedCountRecord.ref { mappings at "old" } with emptyMap<String, String>(),
                    NestedCountRecord.ref { lists at "old" } with emptyList<String>(),
                    NestedCountRecord.ref { sets at "old" } with emptySet<String>(),
                )))).statuses.single())
                assertIs<ChangeSuccess<NestedCountRecord>>(store.execute(NestedCountRecord.change(added.key.change(Change(
                    NestedCountRecord.ref { mappings at "new" } with emptyMap<String, String>(),
                    NestedCountRecord.ref { lists at "new" } with emptyList<String>(),
                    NestedCountRecord.ref { sets at "new" } with emptySet<String>(),
                )))).statuses.single())
                assertIs<ValidationFail<NestedCountRecord>>(store.execute(NestedCountRecord.change(added.key.change(Change(
                    NestedCountRecord.ref { mappings at "overflow" } with emptyMap<String, String>(),
                    NestedCountRecord.ref { lists at "overflow" } with emptyList<String>(),
                    NestedCountRecord.ref { sets at "overflow" } with emptySet<String>(),
                )))).statuses.single())
                assertIs<ChangeSuccess<NestedCountRecord>>(store.execute(NestedCountRecord.change(added.key.change(Change(
                    NestedCountRecord.ref { mappings at "old" } with null,
                    NestedCountRecord.ref { lists at "old" } with null,
                    NestedCountRecord.ref { sets at "old" } with null,
                )))).statuses.single())
                store.close()
                store = open()
                val current = store.execute(NestedCountRecord.get(added.key)).values.single()
                assertEquals(mapOf("new" to emptyMap()), current.values { mappings })
                assertEquals(mapOf("new" to emptyList()), current.values { lists })
                assertEquals(mapOf("new" to emptySet()), current.values { sets })
                assertEquals(mapOf("first" to "present empty parent"), current.values { scalar })
                val dirsAfter = store.getTableDirs(NestedCountRecord)
                store.runTransaction { tr ->
                    for (reference in listOf(NestedCountRecord.ref { mappings }, NestedCountRecord.ref { lists }, NestedCountRecord.ref { sets })) assertContentEquals(byteArrayOf(1), tr.get(packKey(dirsAfter.tablePrefix, added.key.bytes, reference.toStorageByteArray())).awaitResult()!!.copyOfRange(8, 9))
                }
                if (history) assertEquals(initial, store.execute(NestedCountRecord.get(added.key, toVersion = added.version)).values.single().values)
                val absent = assertIs<AddSuccess<NestedCountRecord>>(store.execute(NestedCountRecord.add(
                    NestedCountRecord.create { marker with "no containers" },
                )).statuses.single())
                val inserted = assertIs<ChangeSuccess<NestedCountRecord>>(store.execute(NestedCountRecord.change(absent.key.change(
                    Change(NestedCountRecord.ref { mappings at "supplied" } with emptyMap<String, String>()),
                ))).statuses.single())
                assertIs<ServerFail<NestedCountRecord>>(store.execute(NestedCountRecord.change(absent.key.change(
                    Change(NestedCountRecord.ref { scalar at "orphan" } with "value"),
                ))).statuses.single())
                val unchanged = store.execute(NestedCountRecord.get(absent.key)).values.single()
                assertEquals(mapOf("supplied" to emptyMap()), unchanged.values { mappings })
                assertNull(unchanged.values { scalar })
                assertEquals(inserted.version, unchanged.lastVersion)
            } finally {
                store.close()

            }
        }
    }
}

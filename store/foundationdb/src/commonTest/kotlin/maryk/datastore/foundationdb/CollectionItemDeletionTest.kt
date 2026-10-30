package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.extensions.bytes.toByteArray
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object CollectionDeleteRecord : RootDataModel<CollectionDeleteRecord>() {
    val mapping by map(1u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition(), minSize = 1u)
    val members by set(2u, valueDefinition = StringDefinition(), minSize = 1u)
    val allocated by incrementingMap(3u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition(), minSize = 1u)
}

class CollectionItemDeletionTest {
    @Test
    fun missingAndRepeatedDeletesPreserveCountsAndMinimumSizesAfterReopen() = runTest {
        for (history in listOf(false, true)) {
            val directory = listOf("maryk", "collection-item-deletion-$history-${Random.nextInt()}")
            suspend fun open() = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
                dataModelsById = mapOf(1u to CollectionDeleteRecord), keepAllVersions = history,
            )
            var store = open()
            try {
                val added = assertIs<AddSuccess<CollectionDeleteRecord>>(store.execute(CollectionDeleteRecord.add(
                    CollectionDeleteRecord.create {
                        mapping with mapOf("a" to "A", "b" to "B")
                        members with setOf("a", "b")
                        allocated with mapOf(1u to "A", 2u to "B")
                    },
                )).statuses.single())
                val missing = assertIs<ChangeSuccess<CollectionDeleteRecord>>(store.execute(CollectionDeleteRecord.change(
                    added.key.change(Change(
                        CollectionDeleteRecord.ref { mapping at "missing" } with null,
                        CollectionDeleteRecord.ref { members item "missing" } with null,
                        CollectionDeleteRecord.ref { allocated at 99u } with null,
                    )),
                )).statuses.single())
                assertEquals(added.version, missing.version)
                val dirs = store.getTableDirs(CollectionDeleteRecord)
                store.runTransaction { tr ->
                    for (reference in listOf(CollectionDeleteRecord.ref { mapping }, CollectionDeleteRecord.ref { members }, CollectionDeleteRecord.ref { allocated })) {
                        assertContentEquals(added.version.toByteArray() + byteArrayOf(2), tr.get(packKey(dirs.tablePrefix, added.key.bytes, reference.toStorageByteArray())).awaitResult())
                    }
                }
                val removed = assertIs<ChangeSuccess<CollectionDeleteRecord>>(store.execute(CollectionDeleteRecord.change(
                    added.key.change(Change(
                        CollectionDeleteRecord.ref { mapping at "a" } with null,
                        CollectionDeleteRecord.ref { members item "a" } with null,
                        CollectionDeleteRecord.ref { allocated at 1u } with null,
                    )),
                )).statuses.single())
                val repeated = assertIs<ChangeSuccess<CollectionDeleteRecord>>(store.execute(CollectionDeleteRecord.change(
                    added.key.change(Change(
                        CollectionDeleteRecord.ref { mapping at "a" } with null,
                        CollectionDeleteRecord.ref { members item "a" } with null,
                        CollectionDeleteRecord.ref { allocated at 1u } with null,
                    )),
                )).statuses.single())
                assertEquals(removed.version, repeated.version)
                assertIs<ValidationFail<CollectionDeleteRecord>>(store.execute(CollectionDeleteRecord.change(
                    added.key.change(Change(
                        CollectionDeleteRecord.ref { mapping at "b" } with null,
                        CollectionDeleteRecord.ref { members item "b" } with null,
                        CollectionDeleteRecord.ref { allocated at 2u } with null,
                    )),
                )).statuses.single())
                store.close()
                store = open()
                val current = store.execute(CollectionDeleteRecord.get(added.key)).values.single()
                assertEquals(mapOf("b" to "B"), current.values { mapping })
                assertEquals(setOf("b"), current.values { members })
                assertEquals(mapOf(2u to "B"), current.values { allocated })
                assertEquals(removed.version, current.lastVersion)
                val reopenedDirs = store.getTableDirs(CollectionDeleteRecord)
                store.runTransaction { tr ->
                    for (reference in listOf(CollectionDeleteRecord.ref { mapping }, CollectionDeleteRecord.ref { members }, CollectionDeleteRecord.ref { allocated })) {
                        assertContentEquals(removed.version.toByteArray() + byteArrayOf(1), tr.get(packKey(reopenedDirs.tablePrefix, added.key.bytes, reference.toStorageByteArray())).awaitResult())
                    }
                }
                if (history) {
                    val original = store.execute(CollectionDeleteRecord.get(added.key, toVersion = added.version)).values.single()
                    assertEquals(mapOf("a" to "A", "b" to "B"), original.values { mapping })
                    assertEquals(setOf("a", "b"), original.values { members })
                    assertEquals(mapOf(1u to "A", 2u to "B"), original.values { allocated })
                }
            } finally {
                store.close()
            }
        }
    }
}

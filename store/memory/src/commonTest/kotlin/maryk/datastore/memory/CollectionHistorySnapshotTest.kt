package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.DataModel
import maryk.core.models.fromChanges
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.embed
import maryk.core.properties.references.ListItemReference
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.IsFilter
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.get
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanUpdates
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.responses.updates.processUpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object CollectionHistorySnapshotRecord : RootDataModel<CollectionHistorySnapshotRecord>() {
    val items by list(1u, valueDefinition = StringDefinition())
    val buckets by map(2u, keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()))
    val tags by set(3u, valueDefinition = StringDefinition(), required = false)
    val details by embed(4u, dataModel = { CollectionHistorySnapshotDetails }, required = false)
}

private object CollectionHistorySnapshotDetails : DataModel<CollectionHistorySnapshotDetails>() {
    val value by string(1u, required = false)
}

class CollectionHistorySnapshotTest {
    @Test
    fun currentOnlyStoresExportCompleteCollectionsAfterAnEarlierCellWasOverwritten() = runTest {
        verifySnapshots(keepAllVersions = false)
    }

    @Test
    fun historicStoresReadEachCollectionSnapshotAtItsOwnVersionEvenWithDefaultVersionLimit() = runTest {
        verifySnapshots(keepAllVersions = true)
    }

    @Test
    fun exactStructuralComparisonsUseValuesAtTheRequestedVersion() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to CollectionHistorySnapshotRecord), keepAllVersions = true)
        try {
            val populatedDetails = CollectionHistorySnapshotDetails.create { value with "detail" }
            val emptyDetails = CollectionHistorySnapshotDetails.create(setDefaults = false) {}
            val before = CollectionHistorySnapshotRecord.create {
                items with listOf("item")
                buckets with mapOf("key" to listOf("item"))
                tags with setOf("tag")
                details with populatedDetails
            }
            val added = assertIs<AddSuccess<CollectionHistorySnapshotRecord>>(store.execute(CollectionHistorySnapshotRecord.add(before)).statuses.single())
            assertIs<ChangeSuccess<CollectionHistorySnapshotRecord>>(store.execute(CollectionHistorySnapshotRecord.change(added.key.change(Change(
                CollectionHistorySnapshotRecord.ref { items } with emptyList<String>(),
                CollectionHistorySnapshotRecord.ref { buckets } with emptyMap<String, List<String>>(),
                CollectionHistorySnapshotRecord.ref { tags } with emptySet<String>(),
                CollectionHistorySnapshotRecord.ref { details } with emptyDetails,
            )))).statuses.single())
            val current = listOf<IsFilter>(
                Equals(CollectionHistorySnapshotRecord.ref { items } with emptyList<String>()),
                Equals(CollectionHistorySnapshotRecord.ref { buckets } with emptyMap<String, List<String>>()),
                Equals(CollectionHistorySnapshotRecord.ref { tags } with emptySet<String>()),
                Equals(CollectionHistorySnapshotRecord.ref { details } with emptyDetails),
            )
            val previous = listOf<IsFilter>(
                Equals(CollectionHistorySnapshotRecord.ref { items } with listOf("item")),
                Equals(CollectionHistorySnapshotRecord.ref { buckets } with mapOf("key" to listOf("item"))),
                Equals(CollectionHistorySnapshotRecord.ref { tags } with setOf("tag")),
                Equals(CollectionHistorySnapshotRecord.ref { details } with populatedDetails),
            )
            for (filter in current) assertEquals(1, store.execute(CollectionHistorySnapshotRecord.get(added.key, where = filter)).values.size)
            for (filter in previous) {
                assertEquals(0, store.execute(CollectionHistorySnapshotRecord.get(added.key, where = filter)).values.size)
                assertEquals(1, store.execute(CollectionHistorySnapshotRecord.get(added.key, where = filter, toVersion = added.version)).values.size)
            }
            val exists = Exists(CollectionHistorySnapshotRecord.ref { items }, CollectionHistorySnapshotRecord.ref { buckets },
                CollectionHistorySnapshotRecord.ref { tags }, CollectionHistorySnapshotRecord.ref { details })
            assertEquals(1, store.execute(CollectionHistorySnapshotRecord.get(added.key, where = exists)).values.size)
            val fuzzy = Equals(CollectionHistorySnapshotRecord.ref { items.any() } with "item")
            assertEquals(0, store.execute(CollectionHistorySnapshotRecord.get(added.key, where = fuzzy)).values.size)
            assertEquals(1, store.execute(CollectionHistorySnapshotRecord.get(added.key, where = fuzzy, toVersion = added.version)).values.size)
        } finally {
            store.close()
        }
    }

    private suspend fun verifySnapshots(keepAllVersions: Boolean) {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to CollectionHistorySnapshotRecord), keepAllVersions = keepAllVersions)
        try {
            val before = CollectionHistorySnapshotRecord.create {
                items with listOf("before", "retained")
                buckets with mapOf("bucket" to listOf("before", "retained"))
            }
            val after = CollectionHistorySnapshotRecord.create {
                items with listOf("after", "retained")
                buckets with mapOf("bucket" to listOf("after", "retained"))
            }
            val added = assertIs<AddSuccess<CollectionHistorySnapshotRecord>>(store.execute(CollectionHistorySnapshotRecord.add(before)).statuses.single())
            @Suppress("UNCHECKED_CAST")
            val bucketItem = CollectionHistorySnapshotRecord.getPropertyReferenceByName("buckets.@bucket.@0") as ListItemReference<String, IsPropertyContext>
            val changed = assertIs<ChangeSuccess<CollectionHistorySnapshotRecord>>(store.execute(CollectionHistorySnapshotRecord.change(added.key.change(Change(
                CollectionHistorySnapshotRecord.ref { items at 0u } with "after",
                bucketItem with "after",
            )))).statuses.single())

            val current = store.execute(CollectionHistorySnapshotRecord.getChanges(added.key)).changes.single().changes
            assertEquals(after, CollectionHistorySnapshotRecord.fromChanges(null, current.flatMap { it.changes }))
            for (updates in listOf(
                store.execute(CollectionHistorySnapshotRecord.getUpdates(added.key)).updates,
                store.execute(CollectionHistorySnapshotRecord.scanUpdates()).updates,
            )) {
                var client = emptyList<ValuesWithMetaData<CollectionHistorySnapshotRecord>>()
                for (update in updates) client = processUpdateResponse(update, client)
                assertEquals(after, client.single().values)
                assertEquals(changed.version, client.single().lastVersion)
            }
            if (keepAllVersions) {
                val creation = current.single { it.version == added.version }
                val created = CollectionHistorySnapshotRecord.fromChanges(null, creation.changes)
                assertEquals(before { buckets }, created { buckets })
                val delta = store.execute(CollectionHistorySnapshotRecord.getChanges(added.key, fromVersion = changed.version, maxVersions = 1u)).changes.single().changes
                assertEquals(after, before.change(delta.flatMap { it.changes }))
            }
        } finally {
            store.close()
        }
    }
}

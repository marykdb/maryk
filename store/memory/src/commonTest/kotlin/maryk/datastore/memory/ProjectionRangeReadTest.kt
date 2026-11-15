package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.metric.ValueCount
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.query.filters.Equals
import maryk.core.query.requests.scan
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.models.fromChanges
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

private object ProjectionRangeRecord : RootDataModel<ProjectionRangeRecord>() {
    val title by string(1u)
    val components by list(2u, valueDefinition = StringDefinition())
    val footer by string(300u, required = false)
}

private object ProjectionCreationRecord : RootDataModel<ProjectionCreationRecord>() {
    val title by string(1u)
    val components by list(2u, valueDefinition = StringDefinition())
    val category by string(4u, final = true)
    val footer by string(300u, required = false)
}

class ProjectionRangeReadTest {
    @Test
    fun projectedCreationScansPreserveFiltersMissingFieldsAndLaterChanges() = runTest {
        val model = ProjectionCreationRecord
        val selection = model.graph { listOf(title, footer) }
        for (historyIndex in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to model), keepAllVersions = true,
                keepUpdateHistoryIndex = historyIndex)
            try {
                val added = assertIs<AddSuccess<ProjectionCreationRecord>>(store.execute(model.add(model.create {
                    title with "title"; components with List(64) { "part-$it" }; category with "kept"
                })).statuses.single())
                val filter = Equals(model.category.ref() with "kept")
                val expected = store.execute(model.get(added.key, select = selection)).values.single()
                val creation = store.execute(model.scanChanges(select = selection, where = filter)).changes.single().changes.single()
                assertEquals(added.version, creation.version)
                assertTrue(ObjectCreate in creation.changes)
                assertEquals(expected.values, model.fromChanges(null, creation.changes))
                val update = store.execute(model.scanUpdates(select = selection, where = filter)).updates
                    .filterIsInstance<AdditionUpdate<ProjectionCreationRecord>>().single()
                assertEquals(expected.values, update.values)
                assertEquals(added.version, update.firstVersion)
                assertEquals(added.version, update.version)
                assertTrue(store.execute(model.scanUpdates(select = selection, where = filter,
                    fromVersion = added.version + 1uL)).updates.filterIsInstance<AdditionUpdate<ProjectionCreationRecord>>().isEmpty())

                val changed = assertIs<ChangeSuccess<ProjectionCreationRecord>>(store.execute(model.change(added.key.change(Change(
                    model.footer.ref() with "footer", model.components.ref() with List(128) { "new-$it" },
                )))).statuses.single())
                val updates = store.execute(model.scanUpdates(select = selection, toVersion = changed.version,
                    maxVersions = 2u, where = filter)).updates
                val initial = updates.filterIsInstance<AdditionUpdate<ProjectionCreationRecord>>().single()
                assertEquals(expected.values, initial.values)
                val delta = updates.filterIsInstance<ChangeUpdate<ProjectionCreationRecord>>().single()
                assertEquals(changed.version, delta.version)
                val replayed = initial.values.change(delta.changes)
                assertEquals("footer", replayed { footer })
                assertNull(replayed { components })
            } finally { store.close() }
        }
    }

    @Test
    fun narrowCurrentProjectionPreservesMissingFieldsHistoryAndDeleteVersions() = runTest {
        val model = ProjectionRangeRecord
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to model), keepAllVersions = true)
        val selection = model.graph { listOf(title, footer) }
        try {
            val added = assertIs<AddSuccess<ProjectionRangeRecord>>(store.execute(model.add(model.create {
                title with "title"; components with List(2048) { "part-$it" }
            })).statuses.single())
            val missing = store.execute(model.get(added.key, select = selection)).values.single()
            assertEquals("title", missing.values { title })
            assertNull(missing.values { footer })
            assertNull(missing.values { components })
            assertEquals(added.version, missing.lastVersion)
            val changed = assertIs<ChangeSuccess<ProjectionRangeRecord>>(store.execute(model.change(added.key.change(
                Change(model.footer.ref() with "footer"),
            ))).statuses.single())
            val unrelated = assertIs<ChangeSuccess<ProjectionRangeRecord>>(store.execute(model.change(added.key.change(
                Change(model.components.ref() with List(4096) { "new-$it" }),
            ))).statuses.single())
            val current = store.execute(model.get(added.key, select = selection)).values.single()
            val historical = store.execute(model.get(added.key, select = selection, toVersion = unrelated.version)).values.single()
            assertEquals(historical, current)
            assertEquals(changed.version, current.lastVersion)
            assertEquals("footer", current.values { footer })
            val deleted = assertIs<DeleteSuccess<ProjectionRangeRecord>>(store.execute(model.delete(added.key)).statuses.single())
            val hidden = store.execute(model.get(added.key, select = selection, filterSoftDeleted = false)).values.single()
            assertTrue(hidden.isDeleted)
            assertEquals(deleted.version, hidden.lastVersion)
            assertEquals(store.execute(model.get(added.key, select = selection, filterSoftDeleted = false,
                toVersion = deleted.version)).values.single(), hidden)
            val restored = assertIs<ChangeSuccess<ProjectionRangeRecord>>(store.execute(model.change(
                added.key.change(ObjectSoftDeleteChange(false)),
            )).statuses.single())
            val visible = store.execute(model.get(added.key, select = selection)).values.single()
            assertEquals(restored.version, visible.lastVersion)
            assertEquals(added.version, visible.firstVersion)
            assertEquals("footer", visible.values { footer })
            assertNull(visible.values { components })
            val removed = assertIs<ChangeSuccess<ProjectionRangeRecord>>(store.execute(model.change(
                added.key.change(Change(model.footer.ref() with null)),
            )).statuses.single())
            val noFooter = store.execute(model.get(added.key, select = selection)).values.single()
            assertNull(noFooter.values { footer })
            assertEquals(removed.version, noFooter.lastVersion)
            assertEquals(store.execute(model.get(added.key, select = selection, toVersion = removed.version)).values.single(), noFooter)

            data class Expected(val at: ULong, val footer: String?, val last: ULong, val deleted: Boolean = false)
            val snapshots = listOf(
                Expected(added.version, null, added.version),
                Expected(changed.version, "footer", changed.version),
                Expected(unrelated.version, "footer", changed.version),
                Expected(deleted.version, "footer", deleted.version, true),
                Expected(restored.version, "footer", restored.version),
                Expected(removed.version, null, removed.version),
            )
            for (snapshot in snapshots) {
                val projected = store.execute(model.get(added.key, select = selection, toVersion = snapshot.at,
                    filterSoftDeleted = false)).values.single()
                assertEquals("title", projected.values { title })
                assertEquals(snapshot.footer, projected.values { footer })
                assertNull(projected.values { components })
                assertEquals(added.version, projected.firstVersion)
                assertEquals(snapshot.last, projected.lastVersion)
                assertEquals(snapshot.deleted, projected.isDeleted)
                val scanned = store.execute(model.scan(select = selection, toVersion = snapshot.at,
                    filterSoftDeleted = false, allowTableScan = true, limit = 10u)).values.single()
                assertEquals(projected, scanned)
                val filtered = store.execute(model.scan(select = model.graph { listOf(title) }, toVersion = snapshot.at,
                    where = Equals(model.footer.ref() with "footer"), filterSoftDeleted = false,
                    allowTableScan = true, limit = 10u))
                assertEquals(if (snapshot.footer == "footer") 1 else 0, filtered.values.size)
            }
            assertTrue(store.execute(model.get(added.key, select = selection, toVersion = added.version - 1uL)).values.isEmpty())
            assertIs<DeleteSuccess<ProjectionRangeRecord>>(store.execute(model.delete(added.key, hardDelete = true)).statuses.single())
            val replacement = assertIs<AddSuccess<ProjectionRangeRecord>>(store.execute(model.add(added.key to model.create {
                title with "replacement"; components with List(128) { "replacement-$it" }; footer with "replacement"
            })).statuses.single())
            val archived = store.execute(model.get(added.key, select = selection, toVersion = unrelated.version)).values.single()
            assertEquals("title", archived.values { title })
            assertEquals("footer", archived.values { footer })
            assertEquals(added.version, archived.firstVersion)
            assertEquals(changed.version, archived.lastVersion)
            val archivedScan = store.execute(model.scan(select = selection, toVersion = unrelated.version,
                allowTableScan = true, limit = 10u)).values.single()
            assertEquals(archived, archivedScan)
            val recreated = store.execute(model.get(added.key, select = selection, toVersion = replacement.version)).values.single()
            assertEquals("replacement", recreated.values { title })
            assertEquals(replacement.version, recreated.firstVersion)
        } finally { store.close() }
    }

    @Test
    fun currentScansKeepPagingHiddenAggregatesAndDeletionVersions() = runTest {
        val model = ProjectionRangeRecord
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to model), keepAllVersions = true)
        try {
            val added = store.execute(model.add(*(0 until 10).map { i -> model.create {
                title with "title-$i"; components with List(128) { "component-$it" }; footer with "footer"
            } }.toTypedArray())).statuses.map { assertIs<AddSuccess<ProjectionRangeRecord>>(it) }
            val deleted = assertIs<DeleteSuccess<ProjectionRangeRecord>>(store.execute(model.delete(added.first().key)).statuses.single())
            val select = model.graph { listOf(title) }
            val request = model.scan(select = select, allowTableScan = true, limit = 4u,
                where = Equals(model.footer.ref() with "footer"),
                aggregations = Aggregations("count" to ValueCount(model.footer.ref())))
            var page = store.execute(request)
            val seen = mutableSetOf<String>()
            do {
                assertEquals(page.values.size.toULong(), (page.aggregations!!.namedAggregations["count"] as ValueCountResponse<*>).value)
                for (row in page.values) {
                    assertNull(row.values { components }); assertNull(row.values { footer })
                    assertTrue(seen.add(row.values { title }!!))
                }
                val cursor = page.nextCursor ?: break
                page = store.execute(model.scan(select = select, allowTableScan = true, limit = 4u,
                    where = Equals(model.footer.ref() with "footer"),
                    aggregations = Aggregations("count" to ValueCount(model.footer.ref())), cursor = cursor))
            } while (true)
            assertEquals((1 until 10).map { "title-$it" }.toSet(), seen)
            val all = store.execute(model.scan(select = select, allowTableScan = true, limit = 20u, filterSoftDeleted = false))
            val hidden = all.values.single { it.key == added.first().key }
            assertTrue(hidden.isDeleted); assertEquals(deleted.version, hidden.lastVersion)
            val historical = store.execute(model.scan(select = select, allowTableScan = true, limit = 20u, toVersion = added.last().version))
            assertEquals(10, historical.values.size)
            assertTrue(historical.values.none { it.isDeleted })
        } finally { store.close() }
    }

    @Test
    fun repeatedCurrentFullReadsKeepOrderOwnershipVersionsAndRequestBoundaries() = runTest {
        val model = ProjectionRangeRecord
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to model), keepAllVersions = true)
        try {
            val expected = List(8192) { "part-$it" }
            val added = store.execute(model.add(*listOf("a", "b").map { title -> model.create {
                this.title with title; components with expected; footer with "footer-$title"
            } }.toTypedArray())).statuses.map { assertIs<AddSuccess<ProjectionRangeRecord>>(it) }
            val a = added[0]; val b = added[1]
            val missing = Key<ProjectionRangeRecord>(ByteArray(model.Meta.keyByteSize) { 127 })
            val response = store.execute(model.get(a.key, b.key, a.key, missing, b.key, a.key)).values
            assertEquals(listOf(a.key, b.key, a.key, b.key, a.key), response.map { it.key })
            assertEquals(listOf("a", "b", "a", "b", "a"), response.map { it.values { title } })
            @Suppress("UNCHECKED_CAST")
            val first = response.first().values[model.components.ref()] as MutableList<String>
            first[0] = "caller-change"
            assertEquals(expected, response[2].values { components })
            assertEquals(expected, store.execute(model.get(a.key)).values.single().values { components })
            val changedValues = List(8192) { "next-$it" }
            val changed = assertIs<ChangeSuccess<ProjectionRangeRecord>>(store.execute(model.change(a.key.change(
                Change(model.components.ref() with changedValues, model.footer.ref() with "updated"),
            ))).statuses.single())
            val current = store.execute(model.get(a.key, a.key)).values
            assertTrue(current.all { it.values { components } == changedValues && it.values { footer } == "updated" })
            assertTrue(current.all { it.firstVersion == a.version && it.lastVersion == changed.version })
            val historic = store.execute(model.get(a.key, a.key, toVersion = a.version)).values
            assertTrue(historic.all { it.values { components } == expected && it.values { footer } == "footer-a" })
            assertTrue(historic.all { it.lastVersion == a.version })
            val deleted = assertIs<DeleteSuccess<ProjectionRangeRecord>>(store.execute(model.delete(a.key)).statuses.single())
            assertEquals(listOf(b.key), store.execute(model.get(a.key, b.key, a.key)).values.map { it.key })
            val hidden = store.execute(model.get(a.key, a.key, filterSoftDeleted = false)).values
            assertEquals(2, hidden.size)
            assertTrue(hidden.all { it.isDeleted && it.firstVersion == a.version && it.lastVersion == deleted.version })
        } finally { store.close() }
    }

    @Test
    fun selectingCollectionsKeepsOwnedMutableResults() = runTest {
        val model = ProjectionRangeRecord
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to model))
        try {
            val expected = List(128) { "part-$it" }
            val added = assertIs<AddSuccess<ProjectionRangeRecord>>(store.execute(model.add(model.create {
                title with "title"; components with expected; footer with "footer"
            })).statuses.single())
            val selected = store.execute(model.get(added.key, added.key, select = model.graph { listOf(components) })).values
            @Suppress("UNCHECKED_CAST")
            val first = selected[0].values[model.components.ref()] as MutableList<String>
            first[0] = "changed"
            assertEquals(expected, selected[1].values { components })
            assertEquals(expected, store.execute(model.get(added.key)).values.single().values { components })
        } finally { store.close() }
    }
}

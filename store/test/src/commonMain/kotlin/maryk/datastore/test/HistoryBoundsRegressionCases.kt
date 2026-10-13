package maryk.datastore.test

import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.UpdateHistoryCursor
import maryk.core.query.requests.add
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.get
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdateHistory
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.beginTransaction
import maryk.datastore.shared.executeAtomic
import maryk.lib.extensions.compare.compareTo
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Native requests only: every engine must preserve event bounds before paging. */
class HistoryBoundsRegressionCases(private val createStore: suspend (String) -> IsDataStore) {
    suspend fun deletedRecordHistoryPreservesLimitsAndCursorCeiling() = withStore("history-delete-pages") { store ->
        val added = store.addRecord()
        val versions = mutableListOf(added.version)
        repeat(505) { index ->
            versions += assertIs<ChangeSuccess<HistoryBoundsModel>>(
                store.execute(HistoryBoundsModel.change(added.key.change(
                    Change(HistoryBoundsModel.ref { first } with "change $index")
                ))).statuses.single()
            ).version
        }
        val deleted = assertIs<DeleteSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.delete(added.key, hardDelete = false)).statuses.single()
        )
        versions += deleted.version
        val expected = versions.reversed()
        val get = HistoryBoundsModel.getUpdateHistory(added.key, limit = 600u, filterSoftDeleted = false)
        val scan = HistoryBoundsModel.scanUpdateHistory(limit = 600u, filterSoftDeleted = false)
        for (page in listOf(store.execute(get), store.execute(scan))) {
            assertEquals(expected.size, page.updates.size)
            assertEquals(expected, page.updates.map { it.version })
            assertNull(page.nextCursor)
        }

        val getFirst = store.execute(get.copy(limit = 3u))
        val scanFirst = store.execute(scan.copy(limit = 3u))
        assertIs<ChangeSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.change(added.key.change(ObjectSoftDeleteChange(false)))).statuses.single()
        )
        assertPages(expected, getFirst) { cursor -> store.execute(get.copy(limit = 3u, cursor = cursor)) }
        assertPages(expected, scanFirst) { cursor -> store.execute(scan.copy(limit = 3u, cursor = cursor)) }
    }

    suspend fun projectedHistoryKeepsDeletionAndRestorationWithinBounds() = withStore("history-project-bounds") { store ->
        val added = store.addRecord()
        val firstVersion = assertIs<ChangeSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.change(added.key.change(
                Change(HistoryBoundsModel.ref { first } with "selected change")
            ))).statuses.single()
        ).version
        assertIs<ChangeSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.change(added.key.change(
                Change(HistoryBoundsModel.ref { second } with "filter value")
            ))).statuses.single()
        )
        val deleted = assertIs<DeleteSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.delete(added.key, hardDelete = false)).statuses.single()
        ).version
        val restored = assertIs<ChangeSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.change(added.key.change(ObjectSoftDeleteChange(false)))).statuses.single()
        ).version
        val projection = HistoryBoundsModel.graph { listOf(first) }
        val get = HistoryBoundsModel.getUpdateHistory(
            added.key, fromVersion = firstVersion, toVersion = restored, select = projection, filterSoftDeleted = false, limit = 1u
        )
        val scan = HistoryBoundsModel.scanUpdateHistory(
            fromVersion = firstVersion, toVersion = restored, select = projection, filterSoftDeleted = false, limit = 1u
        )
        val expected = listOf(restored, deleted, firstVersion)
        assertPages(expected, store.execute(get)) { cursor -> store.execute(get.copy(cursor = cursor)) }
        assertPages(expected, store.execute(scan)) { cursor -> store.execute(scan.copy(cursor = cursor)) }

        val empty = HistoryBoundsModel.graph { emptyList() }
        for (page in listOf(
            store.execute(get.copy(select = empty, limit = 10u)),
            store.execute(scan.copy(select = empty, limit = 10u))
        )) {
            assertEquals(listOf(restored, deleted), page.updates.map { it.version })
            assertEquals(listOf(ObjectSoftDeleteChange(false)), assertIs<ChangeUpdate<HistoryBoundsModel>>(page.updates[0]).changes)
            assertEquals(listOf(ObjectSoftDeleteChange(true)), assertIs<ChangeUpdate<HistoryBoundsModel>>(page.updates[1]).changes)
        }
        for (page in listOf(
            store.execute(get.copy(toVersion = deleted, filterSoftDeleted = true, limit = 10u)),
            store.execute(scan.copy(toVersion = deleted, filterSoftDeleted = true, limit = 10u))
        )) assertEquals(listOf(firstVersion), page.updates.map { it.version })
        val filter = Equals(HistoryBoundsModel.ref { second } with "filter value")
        for (page in listOf(
            store.execute(get.copy(where = filter, limit = 10u)),
            store.execute(scan.copy(where = filter, limit = 10u))
        )) assertEquals(listOf(restored, deleted), page.updates.map { it.version })
    }

    suspend fun changesAndUpdatesRespectDeletionAndRestorationWindows() = withStore("changes-delete-bounds") { store ->
        val added = store.addRecord()
        val changed = assertIs<ChangeSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.change(added.key.change(
                Change(HistoryBoundsModel.ref { first } with "changed")
            ))).statuses.single()
        ).version
        val deleted = assertIs<DeleteSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.delete(added.key, hardDelete = false)).statuses.single()
        ).version
        val restored = assertIs<ChangeSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.change(added.key.change(ObjectSoftDeleteChange(false)))).statuses.single()
        ).version
        for (upper in listOf(changed, deleted, restored)) {
            val expected = listOf(changed, deleted, restored).filter { it <= upper }
            for (response in listOf(
                store.execute(HistoryBoundsModel.getChanges(added.key, fromVersion = changed, toVersion = upper, maxVersions = 100u, filterSoftDeleted = false)),
                store.execute(HistoryBoundsModel.scanChanges(fromVersion = changed, toVersion = upper, maxVersions = 100u, filterSoftDeleted = false))
            )) {
                assertEquals(expected, response.changes.single().changes.map { it.version })
            }
            for (response in listOf(
                store.execute(HistoryBoundsModel.getUpdates(added.key, fromVersion = changed, toVersion = upper, maxVersions = 100u, filterSoftDeleted = false)),
                store.execute(HistoryBoundsModel.scanUpdates(fromVersion = changed, toVersion = upper, maxVersions = 100u, filterSoftDeleted = false))
            )) {
                assertTrue(response.updates.all { it.version <= upper })
                assertEquals(expected, response.updates.filterIsInstance<ChangeUpdate<HistoryBoundsModel>>().map { it.version })
            }
        }
    }

    suspend fun filteredCursorKeepsTiesAfterLaterSoftDeletion() = withStore("history-filter-cursor-ties") { store ->
        val adds = store.execute(HistoryBoundsModel.add(
            HistoryBoundsModel.create { first with "one"; second with "initial" },
            HistoryBoundsModel.create { first with "two"; second with "initial" },
        )).statuses.map { assertIs<AddSuccess<HistoryBoundsModel>>(it) }
        val keys = adds.map { it.key }.sortedWith { left, right -> left.bytes compareTo right.bytes }
        val version = assertIs<ChangeSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.change(*keys.map { key ->
                key.change(Change(HistoryBoundsModel.ref { first } with "changed"))
            }.toTypedArray())).statuses.first()
        ).version
        val scan = HistoryBoundsModel.scanUpdateHistory(fromVersion = version, limit = 1u)
        val get = HistoryBoundsModel.getUpdateHistory(keys[0], limit = 1u)
        val first = store.execute(scan)
        val firstGet = store.execute(get)
        assertEquals(keys[0], assertIs<ChangeUpdate<HistoryBoundsModel>>(first.updates.single()).key)
        store.execute(HistoryBoundsModel.delete(*keys.toTypedArray(), hardDelete = false))
        val next = store.execute(scan.copy(cursor = assertNotNull(first.nextCursor)))
        assertEquals(keys[1], assertIs<ChangeUpdate<HistoryBoundsModel>>(next.updates.single()).key)
        assertEquals(version, next.updates.single().version)
        assertNull(next.nextCursor)
        val nextGet = store.execute(get.copy(cursor = assertNotNull(firstGet.nextCursor)))
        assertIs<AdditionUpdate<HistoryBoundsModel>>(nextGet.updates.single())
        assertEquals(adds.first().version, nextGet.updates.single().version)
        assertNull(nextGet.nextCursor)
    }

    suspend fun hardDeleteHistorySurvivesKeyReuseWithinItsWindow() = withStore("history-hard-delete-reuse") { store ->
        val added = store.addRecord()
        val deleted = assertIs<DeleteSuccess<HistoryBoundsModel>>(
            store.execute(HistoryBoundsModel.delete(added.key, hardDelete = true)).statuses.single()
        ).version
        store.execute(HistoryBoundsModel.add(added.key to HistoryBoundsModel.create { first with "new record"; second with "new value" }))
        for (response in listOf(
            store.execute(HistoryBoundsModel.getUpdateHistory(added.key, fromVersion = deleted, toVersion = deleted, filterSoftDeleted = false)),
            store.execute(HistoryBoundsModel.scanUpdateHistory(fromVersion = deleted, toVersion = deleted, filterSoftDeleted = false))
        )) {
            val removal = assertIs<RemovalUpdate<HistoryBoundsModel>>(response.updates.single())
            assertEquals(deleted, removal.version)
            assertEquals(HardDelete, removal.reason)
            assertNull(response.nextCursor)
        }
    }

    suspend fun transactionHistoryPreservesOrderedDurableRevisions() = withStore("history-transaction-revision") { store ->
        val added = store.addRecord()
        val tx = store.beginTransaction()
        tx.execute(HistoryBoundsModel.change(added.key.change(Change(HistoryBoundsModel.ref { first } with "intermediate"))))
        tx.execute(HistoryBoundsModel.change(added.key.change(Change(HistoryBoundsModel.ref { second } with "second final"))))
        tx.execute(HistoryBoundsModel.change(added.key.change(Change(HistoryBoundsModel.ref { first } with "first final"))))
        val commit = tx.commit()
        assertEquals(TransactionStatus.Committed, commit.status)
        val versions = commit.responses.map { response ->
            assertIs<ChangeSuccess<HistoryBoundsModel>>(assertIs<ChangeResponse<HistoryBoundsModel>>(response).statuses.single()).version
        }
        assertEquals(3, versions.distinct().size)
        assertEquals(versions.last(), commit.version)
        for (page in listOf(
            store.execute(HistoryBoundsModel.getUpdateHistory(added.key, fromVersion = versions.first(), toVersion = versions.last())),
            store.execute(HistoryBoundsModel.scanUpdateHistory(fromVersion = versions.first(), toVersion = versions.last()))
        )) {
            assertEquals(versions.reversed(), page.updates.map { it.version })
            val pairs = page.updates.map { update ->
                assertIs<ChangeUpdate<HistoryBoundsModel>>(update).changes.filterIsInstance<Change>().flatMap { it.referenceValuePairs }.single()
            }
            assertEquals(listOf("first final", "second final", "intermediate"), pairs.map { it.value })
            assertEquals(listOf("first", "second", "first"), pairs.map { it.reference.completeName })
            assertNull(page.nextCursor)
        }
    }

    suspend fun atomicRevisionKeepsAllSelectedChangesAndFiltersBeforeLimit() = withStore("history-atomic-payload") { store ->
        val added = store.addRecord()
        val mutation = HistoryBoundsModel.change(added.key.change(Change(
            HistoryBoundsModel.ref { first } with "atomic first",
            HistoryBoundsModel.ref { second } with "included",
        )))
        val committed = store.executeAtomic(AtomicRequest("history-payload", mutation))
        assertEquals(AtomicStatus.Committed, committed.status)
        val version = assertIs<ChangeSuccess<HistoryBoundsModel>>(
            assertIs<ChangeResponse<HistoryBoundsModel>>(committed.responses.single()).statuses.single()
        ).version
        store.execute(HistoryBoundsModel.change(added.key.change(Change(HistoryBoundsModel.ref { second } with "excluded"))))
        val filter = Equals(HistoryBoundsModel.ref { second } with "included")
        for (select in listOf(null, HistoryBoundsModel.graph { listOf(first) })) {
            for (page in listOf(
                store.execute(HistoryBoundsModel.getUpdateHistory(added.key, where = filter, limit = 1u, select = select)),
                store.execute(HistoryBoundsModel.scanUpdateHistory(where = filter, limit = 1u, select = select)),
            )) {
                val event = assertIs<ChangeUpdate<HistoryBoundsModel>>(page.updates.single())
                assertEquals(version, event.version)
                val pairs = event.changes.filterIsInstance<Change>().flatMap { it.referenceValuePairs }
                assertEquals(if (select == null) listOf("atomic first", "included") else listOf("atomic first"), pairs.map { it.value })
                assertNull(page.nextCursor, "Excluded events must not create a lookahead cursor")
            }
        }
    }

    suspend fun rollbackAndSavepointsKeepDurableHistoryAndHistoricalReads() = withStore("history-savepoint") { store ->
        val added = store.addRecord()
        val rolledBack = store.beginTransaction()
        rolledBack.execute(HistoryBoundsModel.change(added.key.change(Change(HistoryBoundsModel.ref { first } with "rolled back"))))
        rolledBack.rollback()
        assertEquals(listOf(added.version), store.execute(HistoryBoundsModel.getUpdateHistory(added.key)).updates.map { it.version })

        val tx = store.beginTransaction()
        tx.execute(HistoryBoundsModel.change(added.key.change(Change(HistoryBoundsModel.ref { first } with "kept"))))
        val point = tx.savepoint()
        tx.execute(HistoryBoundsModel.change(added.key.change(Change(HistoryBoundsModel.ref { second } with "discarded"))))
        tx.rollbackTo(point)
        val historical = tx.execute(HistoryBoundsModel.get(added.key, toVersion = added.version)).response.values.single()
        assertEquals("initial", historical.values[HistoryBoundsModel.ref { first }])
        assertEquals("initial", historical.values[HistoryBoundsModel.ref { second }])
        assertTrue(historical.lastVersion <= added.version)
        val commit = tx.commit()
        assertEquals(TransactionStatus.Committed, commit.status)
        val version = assertIs<ChangeSuccess<HistoryBoundsModel>>(
            assertIs<ChangeResponse<HistoryBoundsModel>>(commit.responses.single()).statuses.single()
        ).version
        val history = store.execute(HistoryBoundsModel.getUpdateHistory(added.key, fromVersion = added.version + 1uL))
        val event = assertIs<ChangeUpdate<HistoryBoundsModel>>(history.updates.single())
        assertEquals(version, event.version)
        assertEquals(listOf("kept"), event.changes.filterIsInstance<Change>().flatMap { it.referenceValuePairs }.map { it.value })
        assertNull(history.nextCursor)
        val current = store.execute(HistoryBoundsModel.get(added.key)).values.single()
        assertEquals("initial", current.values[HistoryBoundsModel.ref { second }])
        val empty = store.execute(HistoryBoundsModel.getUpdateHistory(added.key, fromVersion = version + 1uL))
        assertTrue(empty.updates.isEmpty())
        assertNull(empty.nextCursor)
    }

    private suspend fun assertPages(
        expected: List<ULong>,
        first: UpdatesResponse<HistoryBoundsModel>,
        fetch: suspend (UpdateHistoryCursor) -> UpdatesResponse<HistoryBoundsModel>,
    ) {
        var page = first
        val versions = mutableListOf<ULong>()
        for (pageIndex in expected.indices) {
            assertTrue(page.updates.isNotEmpty())
            versions += page.updates.map { it.version }
            val cursor = page.nextCursor ?: break
            page = fetch(cursor)
        }
        assertEquals(expected, versions)
        assertNull(page.nextCursor)
    }

    private suspend fun IsDataStore.addRecord() = assertIs<AddSuccess<HistoryBoundsModel>>(
        execute(HistoryBoundsModel.add(HistoryBoundsModel.create { first with "initial"; second with "initial" })).statuses.single()
    )

    private suspend fun withStore(name: String, block: suspend (IsDataStore) -> Unit) {
        val store = createStore(name)
        try { block(store) } finally { store.close() }
    }
}

object HistoryBoundsModel : RootDataModel<HistoryBoundsModel>(minimumKeyScanByteRange = 0u) {
    val first by string(1u)
    val second by string(2u)
}

val historyBoundsModels = mapOf(1u to HistoryBoundsModel)

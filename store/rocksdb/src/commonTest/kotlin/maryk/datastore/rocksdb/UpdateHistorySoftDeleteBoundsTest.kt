package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getUpdateHistory
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateHistorySoftDeleteBoundsTest {
    @Test
    fun softDeletionDoesNotConsumeOlderRevisionLimitsOrRepeatAcrossCursors() = runTest {
        withHistoryStore("history-soft-delete-pages") { store ->
            val added = assertIs<AddSuccess<SimpleMarykModel>>(
                store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha initial" })).statuses.single()
            )
            val versions = mutableListOf(added.version)
            repeat(505) { index ->
                versions += assertIs<ChangeSuccess<SimpleMarykModel>>(
                    store.execute(SimpleMarykModel.change(added.key.change(
                        Change(SimpleMarykModel.ref { value } with "ha $index")
                    ))).statuses.single()
                ).version
            }
            versions += assertIs<DeleteSuccess<SimpleMarykModel>>(
                store.execute(SimpleMarykModel.delete(added.key, hardDelete = false)).statuses.single()
            ).version
            val expected = versions.reversed()
            val request = SimpleMarykModel.getUpdateHistory(added.key, limit = 600u, filterSoftDeleted = false)
            val complete = store.execute(request)
            assertEquals(expected, complete.updates.map { it.version })
            assertNull(complete.nextCursor)
            val scan = store.execute(SimpleMarykModel.scanUpdateHistory(limit = 600u, filterSoftDeleted = false))
            assertEquals(expected, scan.updates.map { it.version })
            assertNull(scan.nextCursor)

            var page = store.execute(request.copy(limit = 3u))
            val paged = mutableListOf<ULong>()
            for (pageIndex in expected.indices) {
                assertTrue(page.updates.size <= 3)
                paged += page.updates.map { it.version }
                val cursor = page.nextCursor ?: break
                page = store.execute(request.copy(limit = 3u, cursor = cursor))
                assertTrue(page.updates.isNotEmpty())
            }
            // Use a bounded loop so a broken continuation cannot hang the regression.
            assertEquals(expected, paged)
            assertNull(page.nextCursor)
        }
    }

    @Test
    fun laterDeletionAndRestorationStayOutsideEarlierHistoryWindows() = runTest {
        withHistoryStore("history-soft-delete-bounds") { store ->
            val added = assertIs<AddSuccess<SimpleMarykModel>>(
                store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha initial" })).statuses.single()
            )
            val changed = assertIs<ChangeSuccess<SimpleMarykModel>>(
                store.execute(SimpleMarykModel.change(added.key.change(
                    Change(SimpleMarykModel.ref { value } with "ha changed")
                ))).statuses.single()
            )
            val deleted = assertIs<DeleteSuccess<SimpleMarykModel>>(
                store.execute(SimpleMarykModel.delete(added.key, hardDelete = false)).statuses.single()
            )

            suspend fun assertWindow(upper: ULong, expected: List<ULong>) {
                val record = store.execute(SimpleMarykModel.getUpdateHistory(
                    added.key, limit = 10u, toVersion = upper, filterSoftDeleted = false
                ))
                assertEquals(expected, record.updates.map { it.version })
                assertNull(record.nextCursor)
                val scan = store.execute(SimpleMarykModel.scanUpdateHistory(
                    limit = 10u, toVersion = upper, filterSoftDeleted = false
                ))
                assertEquals(expected, scan.updates.map { it.version })
                assertNull(scan.nextCursor)
            }

            assertWindow(changed.version, listOf(changed.version, added.version))
            assertWindow(deleted.version, listOf(deleted.version, changed.version, added.version))
            val restored = assertIs<ChangeSuccess<SimpleMarykModel>>(
                store.execute(SimpleMarykModel.change(added.key.change(ObjectSoftDeleteChange(false)))).statuses.single()
            )
            assertWindow(changed.version, listOf(changed.version, added.version))
            assertWindow(deleted.version, listOf(deleted.version, changed.version, added.version))
            val history = store.execute(SimpleMarykModel.getUpdateHistory(added.key, limit = 10u, filterSoftDeleted = false))
            assertEquals(listOf(restored.version, deleted.version, changed.version, added.version), history.updates.map { it.version })
            assertEquals(listOf(ObjectSoftDeleteChange(false)), assertIs<ChangeUpdate<SimpleMarykModel>>(history.updates[0]).changes)
            assertEquals(listOf(ObjectSoftDeleteChange(true)), assertIs<ChangeUpdate<SimpleMarykModel>>(history.updates[1]).changes)
        }
    }

    private suspend fun withHistoryStore(name: String, block: suspend (RocksDBDataStore) -> Unit) {
        val folder = createTestDBFolder(name)
        val store = RocksDBDataStore.open(
            relativePath = folder,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            block(store)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

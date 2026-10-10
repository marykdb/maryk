package maryk.datastore.test

import kotlinx.datetime.LocalDateTime
import maryk.core.exceptions.RequestException
import maryk.core.models.graph
import maryk.core.query.filters.Exists
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getUpdateHistory
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.test.models.TestMarykModel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import maryk.lib.extensions.compare.compareTo

class DataStoreScanUpdateHistoryTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val testKeys = mutableListOf<Key<TestMarykModel>>()
    private var highestInitVersion = ULong.MIN_VALUE

    override val allTests = mapOf(
        "executeScanUpdateHistoryFiltersOutsideSelection" to ::executeScanUpdateHistoryFiltersOutsideSelection,
        "executeScanUpdateHistoryFailsWithoutIndex" to ::executeScanUpdateHistoryFailsWithoutIndex,
        "executeScanUpdateHistoryReturnsVersionOrderedEntries" to ::executeScanUpdateHistoryReturnsVersionOrderedEntries,
        "executeScanUpdateHistoryCanIncludeSoftDeleteAtHistoricVersion" to ::executeScanUpdateHistoryCanIncludeSoftDeleteAtHistoricVersion,
        "executeScanUpdateHistoryCanIncludeHardDelete" to ::executeScanUpdateHistoryCanIncludeHardDelete,
        "executeGetUpdateHistoryReturnsCompleteRevisions" to ::executeGetUpdateHistoryReturnsCompleteRevisions,
        "executeScanUpdateHistoryCursorRetainsVersionTies" to ::executeScanUpdateHistoryCursorRetainsVersionTies,
        "executeHistoryPagingFiltersBeforeLimit" to ::executeHistoryPagingFiltersBeforeLimit,
        "executeGetUpdateHistoryIncludesCreationAndHardDelete" to ::executeGetUpdateHistoryIncludesCreationAndHardDelete,
        "executeHistoryCursorRejectsChangedQuery" to ::executeHistoryCursorRejectsChangedQuery
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(TestMarykModel.add(t0, t1, t2, t3, t4))
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<TestMarykModel>>(status)
            testKeys.add(response.key)
            highestInitVersion = maxOf(highestInitVersion, response.version)
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            TestMarykModel.delete(*testKeys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        testKeys.clear()
        highestInitVersion = ULong.MIN_VALUE
    }

    private suspend fun executeScanUpdateHistoryFailsWithoutIndex() {
        if (dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex) return

        assertFailsWith<RequestException> {
            dataStore.execute(TestMarykModel.scanUpdateHistory(limit = 1u))
        }
        assertFailsWith<RequestException> {
            dataStore.execute(TestMarykModel.getUpdateHistory(testKeys[1], limit = 1u))
        }
    }

    private suspend fun executeScanUpdateHistoryReturnsVersionOrderedEntries() {
        if (!(dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex)) return

        val change1 = Change(TestMarykModel.ref { string } with "ha history 1")
        val version1 = assertStatusIs<ChangeSuccess<*>>(
            dataStore.execute(TestMarykModel.change(testKeys[1].change(change1))).statuses.first()
        ).version

        val change2 = Change(TestMarykModel.ref { string } with "ha history 2")
        val version2 = assertStatusIs<ChangeSuccess<*>>(
            dataStore.execute(TestMarykModel.change(testKeys[3].change(change2))).statuses.first()
        ).version

        val change3 = Change(TestMarykModel.ref { string } with "ha history 3")
        val version3 = assertStatusIs<ChangeSuccess<*>>(
            dataStore.execute(TestMarykModel.change(testKeys[1].change(change3))).statuses.first()
        ).version

        val response = dataStore.execute(
            TestMarykModel.scanUpdateHistory(
                fromVersion = highestInitVersion + 1uL,
                limit = 3u
            )
        )

        assertIs<FetchByUpdateHistoryIndex>(response.dataFetchType)
        val updates = response.updates.map { assertIs<ChangeUpdate<TestMarykModel>>(it) }
        assertEquals(listOf(version3, version2, version1), updates.map { it.version })
        assertEquals(listOf(testKeys[1], testKeys[3], testKeys[1]), updates.map { it.key })
        assertEquals(listOf(change3), updates[0].changes)
        assertEquals(listOf(change2), updates[1].changes)
        assertEquals(listOf(change1), updates[2].changes)
    }

    private suspend fun executeScanUpdateHistoryCanIncludeSoftDeleteAtHistoricVersion() {
        if (!(dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex)) return

        val deleteVersion = assertStatusIs<DeleteSuccess<*>>(
            dataStore.execute(TestMarykModel.delete(testKeys[1], hardDelete = false)).statuses.first()
        ).version

        val filteredResponse = dataStore.execute(
            TestMarykModel.scanUpdateHistory(
                fromVersion = deleteVersion,
                toVersion = deleteVersion,
                limit = 1u,
                filterSoftDeleted = true
            )
        )
        assertEquals(0, filteredResponse.updates.size)

        val response = dataStore.execute(
            TestMarykModel.scanUpdateHistory(
                fromVersion = deleteVersion,
                toVersion = deleteVersion,
                limit = 1u,
                filterSoftDeleted = false
            )
        )

        assertIs<FetchByUpdateHistoryIndex>(response.dataFetchType)
        val update = assertIs<ChangeUpdate<TestMarykModel>>(response.updates.single())
        assertEquals(deleteVersion, update.version)
        assertEquals(testKeys[1], update.key)
        assertEquals(listOf(ObjectSoftDeleteChange(true)), update.changes)
    }

    private suspend fun executeScanUpdateHistoryCanIncludeHardDelete() {
        if (!(dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex)) return

        val addStatus = assertStatusIs<AddSuccess<TestMarykModel>>(
            dataStore.execute(TestMarykModel.add(
                TestMarykModel.create {
                    string with "ha history hard delete"
                    int with 6
                    uint with 999u
                    bool with true
                    double with 6.6
                    dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                }
            )).statuses.first()
        )
        val deleteVersion = assertStatusIs<DeleteSuccess<*>>(
            dataStore.execute(TestMarykModel.delete(addStatus.key, hardDelete = true)).statuses.first()
        ).version

        val response = dataStore.execute(
            TestMarykModel.scanUpdateHistory(
                fromVersion = deleteVersion,
                toVersion = deleteVersion,
                limit = 1u,
            )
        )

        assertIs<FetchByUpdateHistoryIndex>(response.dataFetchType)
        val update = assertIs<RemovalUpdate<TestMarykModel>>(response.updates.single())
        assertEquals(deleteVersion, update.version)
        assertEquals(addStatus.key, update.key)
        assertEquals(HardDelete, update.reason)
    }
    private suspend fun executeGetUpdateHistoryReturnsCompleteRevisions() {
        if (!(dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex)) return
        val key = testKeys[1]
        val firstChange = Change(TestMarykModel.ref { string } with "ha revision one", TestMarykModel.ref { double } with 17.0)
        val firstVersion = assertStatusIs<ChangeSuccess<*>>(
            dataStore.execute(TestMarykModel.change(key.change(firstChange))).statuses.first()
        ).version
        dataStore.execute(TestMarykModel.change(testKeys[3].change(Change(TestMarykModel.ref { string } with "ha unrelated"))))
        val lastChange = Change(TestMarykModel.ref { double } with 19.0)
        val lastVersion = assertStatusIs<ChangeSuccess<*>>(
            dataStore.execute(TestMarykModel.change(key.change(lastChange))).statuses.first()
        ).version
        val request = TestMarykModel.getUpdateHistory(key, fromVersion = highestInitVersion + 1uL, limit = 1u)
        val firstPage = dataStore.execute(request)
        val latest = assertIs<ChangeUpdate<TestMarykModel>>(firstPage.updates.single())
        assertEquals(lastVersion, latest.version)
        assertEquals(listOf(lastChange), latest.changes)
        val cursor = assertNotNull(firstPage.nextCursor)
        // The second page keeps the earlier write complete, even though one field changed again.
        val secondPage = dataStore.execute(request.copy(cursor = cursor, limit = 2u))
        val earlier = assertIs<ChangeUpdate<TestMarykModel>>(secondPage.updates.single())
        assertEquals(key, earlier.key)
        assertEquals(firstVersion, earlier.version)
        assertEquals(listOf(firstChange), earlier.changes)
        assertNull(secondPage.nextCursor)
    }

    private suspend fun executeScanUpdateHistoryCursorRetainsVersionTies() {
        if (!(dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex)) return
        val keys = listOf(testKeys[1], testKeys[3]).sortedWith { left, right -> left.bytes compareTo right.bytes }
        val response = dataStore.execute(TestMarykModel.change(
            keys[0].change(Change(TestMarykModel.ref { string } with "ha tied first")),
            keys[1].change(Change(TestMarykModel.ref { string } with "ha tied second")),
        ))
        val versions = response.statuses.map { assertStatusIs<ChangeSuccess<*>>(it).version }
        assertEquals(versions[0], versions[1])
        val request = TestMarykModel.scanUpdateHistory(fromVersion = highestInitVersion + 1uL, limit = 1u)
        val first = dataStore.execute(request)
        assertEquals(keys[0], assertIs<ChangeUpdate<TestMarykModel>>(first.updates.single()).key)
        val cursor = assertNotNull(first.nextCursor)
        // A new write between pages must not displace the other record at the tied version.
        dataStore.execute(TestMarykModel.change(keys[0].change(Change(TestMarykModel.ref { string } with "ha newer"))))
        val second = dataStore.execute(request.copy(cursor = cursor))
        val update = assertIs<ChangeUpdate<TestMarykModel>>(second.updates.single())
        assertEquals(keys[1], update.key)
        assertEquals(versions[1], update.version)
        assertNull(second.nextCursor)
    }

    private suspend fun executeHistoryPagingFiltersBeforeLimit() {
        if (!(dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex)) return
        val key = testKeys[1]
        val firstVersion = assertStatusIs<ChangeSuccess<*>>(
            dataStore.execute(TestMarykModel.change(key.change(Change(TestMarykModel.ref { string } with "ha selected old")))).statuses.first()
        ).version
        val secondVersion = assertStatusIs<ChangeSuccess<*>>(
            dataStore.execute(TestMarykModel.change(key.change(Change(TestMarykModel.ref { string } with "ha selected new")))).statuses.first()
        ).version
        dataStore.execute(TestMarykModel.change(key.change(Change(TestMarykModel.ref { double } with 27.0))))
        val projection = TestMarykModel.graph { listOf(string) }
        val getRequest = TestMarykModel.getUpdateHistory(key, select = projection, fromVersion = highestInitVersion + 1uL, limit = 1u)
        val first = dataStore.execute(getRequest)
        assertEquals(secondVersion, first.updates.single().version)
        val second = dataStore.execute(getRequest.copy(cursor = assertNotNull(first.nextCursor)))
        assertEquals(firstVersion, second.updates.single().version)
        assertNull(second.nextCursor)
        val scanRequest = TestMarykModel.scanUpdateHistory(select = projection, fromVersion = highestInitVersion + 1uL, limit = 1u)
        val scanFirst = dataStore.execute(scanRequest)
        assertEquals(secondVersion, scanFirst.updates.single().version)
        val scanSecond = dataStore.execute(scanRequest.copy(cursor = assertNotNull(scanFirst.nextCursor)))
        assertEquals(firstVersion, scanSecond.updates.single().version)
        assertNull(scanSecond.nextCursor)
        // The filter references a field excluded from the returned projection.
        val filter = Exists(TestMarykModel.ref { double })
        val filteredGet = dataStore.execute(getRequest.copy(where = filter))
        assertEquals(secondVersion, filteredGet.updates.single().version)
        val filteredScan = dataStore.execute(scanRequest.copy(cursor = null, where = filter))
        assertEquals(secondVersion, filteredScan.updates.single().version)
    }

    private suspend fun executeGetUpdateHistoryIncludesCreationAndHardDelete() {
        if (!(dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex)) return
        val key = testKeys[1]
        val creation = dataStore.execute(TestMarykModel.getUpdateHistory(key, limit = 1u, fromVersion = highestInitVersion))
        assertEquals(key, assertIs<AdditionUpdate<TestMarykModel>>(creation.updates.single()).key)
        assertNull(creation.nextCursor)
        val deleteVersion = assertStatusIs<DeleteSuccess<*>>(
            dataStore.execute(TestMarykModel.delete(key, hardDelete = true)).statuses.first()
        ).version
        val deleted = dataStore.execute(TestMarykModel.getUpdateHistory(key, limit = 1u, fromVersion = deleteVersion))
        assertEquals(HardDelete, assertIs<RemovalUpdate<TestMarykModel>>(deleted.updates.single()).reason)
        assertNull(deleted.nextCursor)
        val recreatedVersion = assertStatusIs<AddSuccess<*>>(
            dataStore.execute(TestMarykModel.add(t1)).statuses.first()
        ).version
        val reused = dataStore.execute(TestMarykModel.getUpdateHistory(key, limit = 1u, fromVersion = deleteVersion))
        assertEquals(recreatedVersion, assertIs<AdditionUpdate<TestMarykModel>>(reused.updates.single()).version)
        val oldDelete = dataStore.execute(TestMarykModel.getUpdateHistory(
            key, limit = 1u, fromVersion = deleteVersion, cursor = assertNotNull(reused.nextCursor),
        ))
        assertEquals(deleteVersion, assertIs<RemovalUpdate<TestMarykModel>>(oldDelete.updates.single()).version)
        assertNull(oldDelete.nextCursor)
    }

    private suspend fun executeHistoryCursorRejectsChangedQuery() {
        if (!(dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex)) return
        val request = TestMarykModel.scanUpdateHistory(limit = 1u)
        val cursor = assertNotNull(dataStore.execute(request).nextCursor)
        assertFailsWith<RequestException> {
            dataStore.execute(request.copy(cursor = cursor, filterSoftDeleted = false))
        }
        assertFailsWith<RequestException> {
            dataStore.execute(TestMarykModel.getUpdateHistory(testKeys[1], cursor = cursor))
        }
    }

    private suspend fun executeScanUpdateHistoryFiltersOutsideSelection() {
        if (!(dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex)) return
        val key = testKeys[1]
        val version = assertStatusIs<ChangeSuccess<*>>(
            dataStore.execute(TestMarykModel.change(key.change(
                Change(TestMarykModel.ref { string } with "ha selected history")
            ))).statuses.first()
        ).version
        val response = dataStore.execute(TestMarykModel.scanUpdateHistory(
            select = TestMarykModel.graph { listOf(string) },
            where = Exists(TestMarykModel.ref { double }),
            fromVersion = version,
            toVersion = version,
            limit = 1u,
        ))
        val update = assertIs<ChangeUpdate<TestMarykModel>>(response.updates.single())
        assertEquals(key, update.key)
        assertEquals(version, update.version)
        assertEquals(listOf(Change(TestMarykModel.ref { string } with "ha selected history")), update.changes)
    }

}

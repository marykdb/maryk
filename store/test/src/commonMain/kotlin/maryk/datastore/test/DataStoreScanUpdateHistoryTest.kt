package maryk.datastore.test

import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.datastore.shared.IsDataStore
import maryk.test.models.TestMarykModel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DataStoreScanUpdateHistoryTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val testKeys = mutableListOf<Key<TestMarykModel>>()
    private var highestInitVersion = ULong.MIN_VALUE

    override val allTests = mapOf(
        "executeScanUpdateHistoryFailsWithoutIndex" to ::executeScanUpdateHistoryFailsWithoutIndex,
        "executeScanUpdateHistoryReturnsVersionOrderedEntries" to ::executeScanUpdateHistoryReturnsVersionOrderedEntries
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
    }

    private suspend fun executeScanUpdateHistoryReturnsVersionOrderedEntries() {
        if (!(dataStore.keepAllVersions && dataStore.keepUpdateHistoryIndex)) return

        val change1 = Change(TestMarykModel { string::ref } with "ha history 1")
        val version1 = assertStatusIs<ChangeSuccess<*>>(
            dataStore.execute(TestMarykModel.change(testKeys[1].change(change1))).statuses.first()
        ).version

        val change2 = Change(TestMarykModel { string::ref } with "ha history 2")
        val version2 = assertStatusIs<ChangeSuccess<*>>(
            dataStore.execute(TestMarykModel.change(testKeys[3].change(change2))).statuses.first()
        ).version

        val change3 = Change(TestMarykModel { string::ref } with "ha history 3")
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
}

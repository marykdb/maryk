package maryk.datastore.test

import kotlinx.datetime.LocalDateTime
import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.orders.Order.Companion.descending
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scanChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.expect

class DataStoreScanChangesTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<Log>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleScanChangesRequest" to ::executeSimpleScanChangesRequest,
        "executeSimpleScanReversedChangesRequest" to ::executeSimpleScanReversedChangesRequest,
        "executeScanChangesOnAscendingIndexRequest" to ::executeScanChangesOnAscendingIndexRequest,
        "executeScanChangesOnDescendingIndexRequest" to ::executeScanChangesOnDescendingIndexRequest,
        "executeScanChangesRequestWithLimit" to ::executeScanChangesRequestWithLimit,
        "executeScanChangesRequestWithToVersion" to ::executeScanChangesRequestWithToVersion,
        "executeScanChangesRequestWithFromVersion" to ::executeScanChangesRequestWithFromVersion,
        "executeScanChangesRequestWithSelect" to ::executeScanChangesRequestWithSelect,
        "executeScanChangesRequestWithMaxVersions" to ::executeScanChangesRequestWithMaxVersions
    )

    private val logs = arrayOf(
        Log("Something happened", timestamp = LocalDateTime(2018, 11, 14, 11, 22, 33, 40000000)),
        Log("Something else happened", timestamp = LocalDateTime(2018, 11, 14, 12, 0, 0, 0)),
        Log("Something REALLY happened", timestamp = LocalDateTime(2018, 11, 14, 12, 33, 22, 111000000)),
        Log("WRONG", ERROR, LocalDateTime(2018, 11, 14, 13, 0, 2, 0))
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            Log.add(*logs)
        )
        addResponse.statuses.forEach { status ->
            val response = assertIs<AddSuccess<Log>>(status)
            keys.add(response.key)
            if (response.version < lowestVersion) {
                // Add lowest version for scan test
                lowestVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            Log.delete(*keys.toTypedArray(), hardDelete = true)
        )
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeSimpleScanChangesRequest() {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2])
        )

        assertEquals(3, scanResponse.changes.size)

        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.changes[0].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(
                            Log { message::ref } with "Something REALLY happened",
                            Log { severity::ref } with INFO,
                            Log { timestamp::ref } with LocalDateTime(2018, 11, 14, 12, 33, 22, 111000000)
                        )
                    ))
                )
            ) {
                it.changes
            }
            assertEquals(keys[2], it.key)
            assertNull(it.sortingKey)
        }
        scanResponse.changes[1].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(
                            Log { message::ref } with "Something else happened",
                            Log { severity::ref } with INFO,
                            Log { timestamp::ref } with LocalDateTime(2018, 11, 14, 12, 0)
                        )
                    ))
                )
            ) { it.changes }
            assertEquals(keys[1], it.key)
            assertNull(it.sortingKey)
        }
        scanResponse.changes[2].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(
                            Log { message::ref } with "Something happened",
                            Log { severity::ref } with INFO,
                            Log { timestamp::ref } with LocalDateTime(2018, 11, 14, 11, 22, 33, 40000000)
                        )
                    ))
                )
            ) {
                it.changes
            }
            assertEquals(keys[0], it.key)
            assertNull(it.sortingKey)
        }
    }

    private suspend fun executeSimpleScanReversedChangesRequest() {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], order = descending)
        )

        expect(2) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.changes[0].apply {
            assertEquals(keys[2], key)
            assertNull(sortingKey)
        }
        scanResponse.changes[1].apply {
            assertEquals(keys[3], key)
            assertNull(sortingKey)
        }
    }

    private suspend fun executeScanChangesOnAscendingIndexRequest() {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[0], order = Log { severity::ref }.ascending())
        )

        expect(2) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.changes[0].apply {
            assertEquals(keys[0], key)
            assertEquals(Bytes("AAECf///pBP6hn/XAAE"), sortingKey)
        }
        scanResponse.changes[1].apply {
            assertEquals(keys[3], key)
            assertEquals(Bytes("AAMCf///pBPjrX//AAM"), sortingKey)
        }
    }

    private suspend fun executeScanChangesOnDescendingIndexRequest() {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[3], order = Log { severity::ref }.descending(), limit = 3u)
        )

        expect(3) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.changes[0].apply {
            assertEquals(keys[3], key)
            assertEquals(Bytes("AAMCf///pBPjrX//AAM"), sortingKey)
        }
        scanResponse.changes[1].apply {
            assertEquals(keys[0], key)
            assertEquals(Bytes("AAECf///pBP6hn/XAAE"), sortingKey)
        }
    }

    private suspend fun executeScanChangesRequestWithLimit() {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], limit = 1u)
        )

        expect(1) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.changes[0].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(
                            Log { message::ref } with "Something REALLY happened",
                            Log { severity::ref } with INFO,
                            Log { timestamp::ref } with LocalDateTime(2018, 11, 14, 12, 33, 22, 111000000)
                        )
                    ))
                )
            ) {
                it.changes
            }
            expect(keys[2]) { it.key }
        }
    }

    private suspend fun executeScanChangesRequestWithToVersion() {
        if (dataStore.keepAllVersions) {
            val scanResponse = dataStore.execute(
                Log.scanChanges(startKey = keys[2], toVersion = lowestVersion - 1uL)
            )

            expect(0) { scanResponse.changes.size }
        } else {
            assertFailsWith<RequestException> {
                dataStore.execute(
                    Log.scanChanges(startKey = keys[2], toVersion = lowestVersion - 1uL)
                )
            }
        }
    }

    private suspend fun executeScanChangesRequestWithFromVersion() {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], fromVersion = lowestVersion + 1uL)
        )

        expect(0) { scanResponse.changes.size }
    }

    private suspend fun executeScanChangesRequestWithSelect() {
        val scanResponse = dataStore.execute(
            Log.scanChanges(
                startKey = keys[2],
                select = Log.graph {
                    listOf(
                        timestamp
                    )
                }
            )
        )

        expect(3) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.changes[0].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(
                            Log { timestamp::ref } with LocalDateTime(2018, 11, 14, 12, 33, 22, 111000000)
                        )
                    ))
                )
            ) { it.changes }
            expect(keys[2]) { it.key }
        }
    }

    private suspend fun executeScanChangesRequestWithMaxVersions() {
        if (dataStore.keepAllVersions) {
            val collectedVersions = mutableListOf<ULong>()

            val change1 = Change(Log { message::ref } with "A change 1")
            dataStore.execute(
                Log.change(
                    keys[2].change(change1)
                )
            ).also {
                assertIs<ChangeSuccess<Log>>(it.statuses.first()).apply {
                    collectedVersions.add(version)
                }
            }

            val change2 = Change(Log { message::ref } with "A change 2")
            dataStore.execute(
                Log.change(
                    keys[2].change(change2)
                )
            ).also {
                assertIs<ChangeSuccess<Log>>(it.statuses.first()).apply {
                    collectedVersions.add(version)
                }
            }

            val change3 = Change(Log { message::ref } with "A change 3")
            dataStore.execute(
                Log.change(
                    keys[2].change(change3)
                )
            ).also {
                assertIs<ChangeSuccess<Log>>(it.statuses.first()).apply {
                    collectedVersions.add(version)
                }
            }

            val scanResponse = dataStore.execute(
                Log.scanChanges(
                    startKey = keys[2],
                    maxVersions = 2u
                )
            )

            expect(3) { scanResponse.changes.size }

            // Mind that Log is sorted in reverse, so it goes back in time going forward
            scanResponse.changes[0].let {
                expect(
                    listOf(
                        VersionedChanges(version = lowestVersion, changes = listOf(
                            ObjectCreate,
                            Change(
                                Log { severity::ref } with INFO,
                                Log { timestamp::ref } with LocalDateTime(2018, 11, 14, 12, 33, 22, 111000000)
                            )
                        )),
                        VersionedChanges(version = collectedVersions[1], changes = listOf(change2)),
                        VersionedChanges(version = collectedVersions[2], changes = listOf(change3))
                    )
                ) { it.changes }
                expect(keys[2]) { it.key }
            }
        } else {
            assertFailsWith<RequestException> {
                dataStore.execute(
                    Log.scanChanges(
                        startKey = keys[2],
                        maxVersions = 2u
                    )
                )
            }
        }
    }
}

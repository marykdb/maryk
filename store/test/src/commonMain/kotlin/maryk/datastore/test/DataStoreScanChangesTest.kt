package maryk.datastore.test

import maryk.core.exceptions.RequestException
import maryk.core.properties.types.DateTime
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
import maryk.test.assertType
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import maryk.test.runSuspendingTest
import kotlin.test.assertFailsWith
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
        Log("Something happened", timestamp = DateTime(2018, 11, 14, 11, 22, 33, 40)),
        Log("Something else happened", timestamp = DateTime(2018, 11, 14, 12, 0, 0, 0)),
        Log("Something REALLY happened", timestamp = DateTime(2018, 11, 14, 12, 33, 22, 111)),
        Log("WRONG", ERROR, DateTime(2018, 11, 14, 13, 0, 2, 0))
    )

    override fun initData() {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                Log.add(*logs)
            )
            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<Log>>(status)
                keys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
            }
        }
    }

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                Log.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private fun executeSimpleScanChangesRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2])
        )

        expect(3) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(
                            Log { message::ref } with "Something REALLY happened",
                            Log { severity::ref } with INFO,
                            Log { timestamp::ref } with DateTime(2018, 11, 14, 12, 33, 22, 111)
                        )
                    ))
                )
            ) {
                it.changes
            }
            expect(keys[2]) { it.key }
        }
        scanResponse.changes[1].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(
                            Log { message::ref } with "Something else happened",
                            Log { severity::ref } with INFO,
                            Log { timestamp::ref } with DateTime(2018, 11, 14, 12, 0)
                        )
                    ))
                )
            ) { it.changes }
            expect(keys[1]) { it.key }
        }
        scanResponse.changes[2].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(
                            Log { message::ref } with "Something happened",
                            Log { severity::ref } with INFO,
                            Log { timestamp::ref } with DateTime(2018, 11, 14, 11, 22, 33, 40)
                        )
                    ))
                )
            ) {
                it.changes
            }
            expect(keys[0]) { it.key }
        }
    }

    private fun executeSimpleScanReversedChangesRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], order = descending)
        )

        expect(2) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].apply {
            expect(keys[2]) { key }
        }
        scanResponse.changes[1].apply {
            expect(keys[3]) { key }
        }
    }

    private fun executeScanChangesOnAscendingIndexRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[0], order = Log { severity::ref }.ascending())
        )

        expect(2) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].apply {
            expect(keys[0]) { key }
        }
        scanResponse.changes[1].apply {
            expect(keys[3]) { key }
        }
    }

    private fun executeScanChangesOnDescendingIndexRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[3], order = Log { severity::ref }.descending(), limit = 3u)
        )

        expect(3) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].apply {
            expect(keys[3]) { key }
        }
        scanResponse.changes[1].apply {
            expect(keys[0]) { key }
        }
    }

    private fun executeScanChangesRequestWithLimit() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], limit = 1u)
        )

        expect(1) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(
                            Log { message::ref } with "Something REALLY happened",
                            Log { severity::ref } with INFO,
                            Log { timestamp::ref } with DateTime(2018, 11, 14, 12, 33, 22, 111)
                        )
                    ))
                )
            ) {
                it.changes
            }
            expect(keys[2]) { it.key }
        }
    }

    private fun executeScanChangesRequestWithToVersion() = runSuspendingTest {
        if (dataStore.keepAllVersions) {
            val scanResponse = dataStore.execute(
                Log.scanChanges(startKey = keys[2], toVersion = lowestVersion - 1uL)
            )

            expect(0) { scanResponse.changes.size }
        } else {
            assertFailsWith<RequestException> {
                runSuspendingTest {
                    dataStore.execute(
                        Log.scanChanges(startKey = keys[2], toVersion = lowestVersion - 1uL)
                    )
                }
            }
        }
    }

    private fun executeScanChangesRequestWithFromVersion() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], fromVersion = lowestVersion + 1uL)
        )

        expect(0) { scanResponse.changes.size }
    }

    private fun executeScanChangesRequestWithSelect() = runSuspendingTest {
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

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
                        ObjectCreate,
                        Change(
                            Log { timestamp::ref } with DateTime(2018, 11, 14, 12, 33, 22, 111)
                        )
                    ))
                )
            ) { it.changes }
            expect(keys[2]) { it.key }
        }
    }

    private fun executeScanChangesRequestWithMaxVersions() = runSuspendingTest {
        if (dataStore.keepAllVersions) {
            val collectedVersions = mutableListOf<ULong>()

            val change1 = Change(Log { message::ref } with "A change 1")
            dataStore.execute(
                Log.change(
                    keys[2].change(change1)
                )
            ).also {
                assertType<ChangeSuccess<Log>>(it.statuses.first()).apply {
                    collectedVersions.add(version)
                }
            }

            val change2 = Change(Log { message::ref } with "A change 2")
            dataStore.execute(
                Log.change(
                    keys[2].change(change2)
                )
            ).also {
                assertType<ChangeSuccess<Log>>(it.statuses.first()).apply {
                    collectedVersions.add(version)
                }
            }

            val change3 = Change(Log { message::ref } with "A change 3")
            dataStore.execute(
                Log.change(
                    keys[2].change(change3)
                )
            ).also {
                assertType<ChangeSuccess<Log>>(it.statuses.first()).apply {
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

            // Mind that Log is sorted in reverse so it goes back in time going forward
            scanResponse.changes[0].let {
                expect(
                    listOf(
                        VersionedChanges(version = lowestVersion, changes = listOf(
                            ObjectCreate,
                            Change(
                                Log { severity::ref } with INFO,
                                Log { timestamp::ref } with DateTime(2018, 11, 14, 12, 33, 22, 111)
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

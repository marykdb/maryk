package maryk.datastore.memory

import maryk.core.properties.types.DateTime
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.orders.Order.Companion.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scanChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.assertType
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.expect

class InMemoryDataStoreScanChangesTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<Log>>()
    private var lowestVersion = ULong.MAX_VALUE

    private val logs = arrayOf(
        Log("Something happened", timestamp = DateTime(2018, 11, 14, 11, 22, 33, 40)),
        Log("Something else happened", timestamp = DateTime(2018, 11, 14, 12, 0, 0, 0)),
        Log("Something REALLY happened", timestamp = DateTime(2018, 11, 14, 12, 33, 22, 111)),
        Log("WRONG", ERROR, DateTime(2018, 11, 14, 13, 0, 2, 0))
    )

    init {
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

    @Test
    fun executeSimpleScanChangesRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2])
        )

        expect(3) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
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

    @Test
    fun executeSimpleScanReversedChangesRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], order = descending)
        )

        expect(3) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].apply {
            expect(keys[0]) { key }
        }
        scanResponse.changes[1].apply {
            expect(keys[1]) { key }
        }
        scanResponse.changes[2].apply {
            expect(keys[2]) { key }
        }
    }

    @Test
    fun executeScanChangesRequestWithLimit() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], limit = 1u)
        )

        expect(1) { scanResponse.changes.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].let {
            expect(
                listOf(
                    VersionedChanges(version = lowestVersion, changes = listOf(
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

    @Test
    fun executeScanChangesRequestWithToVersion() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], toVersion = lowestVersion - 1uL)
        )

        expect(0) { scanResponse.changes.size }
    }

    @Test
    fun executeScanChangesRequestWithFromVersion() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], fromVersion = lowestVersion + 1uL)
        )

        expect(0) { scanResponse.changes.size }
    }

    @Test
    fun executeScanChangesRequestWithSelect() = runSuspendingTest {
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
                        Change(
                            Log { timestamp::ref } with DateTime(2018, 11, 14, 12, 33, 22, 111)
                        )
                    ))
                )
            ) { it.changes }
            expect(keys[2]) { it.key }
        }
    }
}

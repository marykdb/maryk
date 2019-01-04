@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory

import maryk.core.properties.types.DateTime
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scanChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
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
                val response = shouldBeOfType<AddSuccess<Log>>(status)
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

        scanResponse.changes.size shouldBe 3

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].let {
            it.changes shouldBe listOf(
                VersionedChanges(version = lowestVersion, changes = listOf(
                    Change(
                        Log.ref { message } with "Something REALLY happened",
                        Log.ref { severity } with INFO,
                        Log.ref { timestamp } with DateTime(2018, 11, 14, 12, 33, 22, 111)
                    )
                ))
            )
            it.key shouldBe keys[2]
        }
        scanResponse.changes[1].let {
            it.changes shouldBe listOf(
                VersionedChanges(version = lowestVersion, changes = listOf(
                    Change(
                        Log.ref { message } with "Something else happened",
                        Log.ref { severity } with INFO,
                        Log.ref { timestamp } with DateTime(2018, 11, 14, 12, 0)
                    )
                ))
            )
            it.key shouldBe keys[1]
        }
        scanResponse.changes[2].let {
            it.changes shouldBe listOf(
                VersionedChanges(version = lowestVersion, changes = listOf(
                    Change(
                        Log.ref { message } with "Something happened",
                        Log.ref { severity } with INFO,
                        Log.ref { timestamp } with DateTime(2018, 11, 14, 11, 22, 33, 40)
                    )
                ))
            )
            it.key shouldBe keys[0]
        }
    }

    @Test
    fun executeScanChangesRequestWithLimit() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], limit = 1u)
        )

        scanResponse.changes.size shouldBe 1

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].let {
            it.changes shouldBe listOf(
                VersionedChanges(version = lowestVersion, changes = listOf(
                    Change(
                        Log.ref { message } with "Something REALLY happened",
                        Log.ref { severity } with INFO,
                        Log.ref { timestamp } with DateTime(2018, 11, 14, 12, 33, 22, 111)
                    )
                ))
            )
            it.key shouldBe keys[2]
        }
    }

    @Test
    fun executeScanChangesRequestWithToVersion() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], toVersion = lowestVersion - 1uL)
        )

        scanResponse.changes.size shouldBe 0
    }

    @Test
    fun executeScanChangesRequestWithFromVersion() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scanChanges(startKey = keys[2], fromVersion = lowestVersion + 1uL)
        )

        scanResponse.changes.size shouldBe 0
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

        scanResponse.changes.size shouldBe 3

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.changes[0].let {
            it.changes shouldBe listOf(
                VersionedChanges(version = lowestVersion, changes = listOf(
                    Change(
                        Log.ref { timestamp } with DateTime(2018, 11, 14, 12, 33, 22, 111)
                    )
                ))
            )
            it.key shouldBe keys[2]
        }
    }
}

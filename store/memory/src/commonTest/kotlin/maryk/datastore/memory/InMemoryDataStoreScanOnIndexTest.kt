package maryk.datastore.memory

import maryk.core.properties.types.DateTime
import maryk.core.properties.types.Key
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.Log
import maryk.test.models.Log.Properties.severity
import maryk.test.models.Severity.DEBUG
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class InMemoryDataStoreScanOnIndexTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<Log>>()
    private var lowestVersion = ULong.MAX_VALUE

    private val logs = arrayOf(
        Log("Something happened", INFO, DateTime(2018, 11, 14, 11, 22, 33, 40)),
        Log("Something else happened", DEBUG, DateTime(2018, 11, 14, 12, 0, 0, 0)),
        Log("Something REALLY happened", INFO, DateTime(2018, 11, 14, 12, 33, 22, 111)),
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
    fun executeSimpleIndexScanRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(order = severity.ref().ascending())
        )

        scanResponse.values.size shouldBe 4

        // Sorted on severity
        scanResponse.values[0].let {
            it.values shouldBe logs[2]
            it.key shouldBe keys[2]
        }
        scanResponse.values[1].let {
            it.values shouldBe logs[0]
            it.key shouldBe keys[0]
        }
        scanResponse.values[2].let {
            it.values shouldBe logs[1]
            it.key shouldBe keys[1]
        }
        scanResponse.values[3].let {
            it.values shouldBe logs[3]
            it.key shouldBe keys[3]
        }
    }

    @Test
    fun executeSimpleIndexScanRequestReverseOrder() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(order = severity.ref().descending())
        )

        scanResponse.values.size shouldBe 4

        // Sorted on severity
        scanResponse.values[0].let {
            it.values shouldBe logs[3]
            it.key shouldBe keys[3]
        }
        scanResponse.values[1].let {
            it.values shouldBe logs[1]
            it.key shouldBe keys[1]
        }
        scanResponse.values[2].let {
            it.values shouldBe logs[0]
            it.key shouldBe keys[0]
        }
        scanResponse.values[3].let {
            it.values shouldBe logs[2]
            it.key shouldBe keys[2]
        }
    }

    @Test
    fun executeIndexScanRequestWithLimit() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(limit = 1u, order = severity.ref().ascending())
        )

        scanResponse.values.size shouldBe 1

        scanResponse.values[0].let {
            it.values shouldBe logs[2]
            it.key shouldBe keys[2]
        }
    }

    @Test
    fun executeIndexScanRequestWithToVersion() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(toVersion = lowestVersion - 1uL, order = severity.ref().ascending())
        )

        scanResponse.values.size shouldBe 0
    }

    @Test
    fun executeIndexScanRequestWithSelect() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                select = Log.graph {
                    listOf(
                        timestamp,
                        severity
                    )
                },
                order = severity.ref().ascending()
            )
        )

        scanResponse.values.size shouldBe 4

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.values[0].let {
            it.values shouldBe Log.values {
                mapNonNulls(
                    this.severity with INFO,
                    this.timestamp with DateTime(2018, 11, 14, 12, 33, 22, 111)
                )
            }
            it.key shouldBe keys[2]
        }
    }

    @Test
    fun executeSimpleIndexFilterScanRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                filter = Equals(
                    severity.ref() with DEBUG
                ),
                order = severity.ref().ascending()
            )
        )

        scanResponse.values.size shouldBe 1

        scanResponse.values[0].let {
            it.values shouldBe logs[1]
            it.key shouldBe keys[1]
        }
    }

    @Test
    fun executeSimpleIndexFilterGreaterScanRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                filter = GreaterThanEquals(
                    severity.ref() with DEBUG
                ),
                order = severity.ref().ascending()
            )
        )

        scanResponse.values.size shouldBe 2

        scanResponse.values[0].let {
            it.values shouldBe logs[1]
            it.key shouldBe keys[1]
        }
        scanResponse.values[1].let {
            it.values shouldBe logs[3]
            it.key shouldBe keys[3]
        }
    }

    @Test
    fun executeSimpleIndexFilterLessScanRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                filter = LessThanEquals(
                    severity.ref() with DEBUG
                ),
                order = severity.ref().descending()
            )
        )

        scanResponse.values.size shouldBe 3

        scanResponse.values[0].let {
            it.values shouldBe logs[1]
            it.key shouldBe keys[1]
        }
        scanResponse.values[1].let {
            it.values shouldBe logs[0]
            it.key shouldBe keys[0]
        }
        scanResponse.values[2].let {
            it.values shouldBe logs[2]
            it.key shouldBe keys[2]
        }
    }
}
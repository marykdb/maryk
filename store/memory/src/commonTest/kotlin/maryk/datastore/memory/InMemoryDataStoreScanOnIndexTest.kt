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
import maryk.test.assertType
import maryk.test.models.Log
import maryk.test.models.Log.Properties.severity
import maryk.test.models.Severity.DEBUG
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.expect

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
    fun executeSimpleIndexScanRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(order = severity.ref().ascending())
        )

        expect(4) { scanResponse.values.size }

        // Sorted on severity
        scanResponse.values[0].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[2].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[3].let {
            expect(logs[3]) { it.values }
            expect(keys[3]) { it.key }
        }
    }

    @Test
    fun executeSimpleIndexScanWithStartKeyRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(startKey = keys[2], order = severity.ref().ascending())
        )

        expect(3) { scanResponse.values.size }

        // Sorted on severity
        scanResponse.values[0].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[2].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
    }

    @Test
    fun executeSimpleIndexScanRequestReverseOrder() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(order = severity.ref().descending())
        )

        expect(4) { scanResponse.values.size }

        // Sorted on severity
        scanResponse.values[0].let {
            expect(logs[3]) { it.values }
            expect(keys[3]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[2].let {
            expect(logs[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[3].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    @Test
    fun executeIndexScanRequestWithLimit() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(limit = 1u, order = severity.ref().ascending())
        )

        expect(1) { scanResponse.values.size }

        scanResponse.values[0].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    @Test
    fun executeIndexScanRequestWithToVersion() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(toVersion = lowestVersion - 1uL, order = severity.ref().ascending())
        )

        expect(0) { scanResponse.values.size }
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

        expect(4) { scanResponse.values.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.values[0].let {
            expect(
                Log.values {
                    mapNonNulls(
                        this.severity with INFO,
                        this.timestamp with DateTime(2018, 11, 14, 12, 33, 22, 111)
                    )
                }
            ) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    @Test
    fun executeSimpleIndexFilterScanRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = Equals(
                    severity.ref() with DEBUG
                ),
                order = severity.ref().ascending()
            )
        )

        expect(1) { scanResponse.values.size }

        scanResponse.values[0].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
    }

    @Test
    fun executeSimpleIndexFilterGreaterScanRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = GreaterThanEquals(
                    severity.ref() with DEBUG
                ),
                order = severity.ref().ascending()
            )
        )

        expect(2) { scanResponse.values.size }

        scanResponse.values[0].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[3]) { it.values }
            expect(keys[3]) { it.key }
        }
    }

    @Test
    fun executeSimpleIndexFilterLessScanRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = LessThanEquals(
                    severity.ref() with DEBUG
                ),
                order = severity.ref().descending()
            )
        )

        expect(3) { scanResponse.values.size }

        scanResponse.values[0].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[2].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
    }
}

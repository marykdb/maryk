package maryk.datastore.memory

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.Max
import maryk.core.aggregations.metric.MaxResponse
import maryk.core.aggregations.metric.Min
import maryk.core.aggregations.metric.MinResponse
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.Key
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Order.Companion.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.assertType
import maryk.test.models.Log
import maryk.test.models.Log.Properties.message
import maryk.test.models.Log.Properties.severity
import maryk.test.models.Log.Properties.timestamp
import maryk.test.models.Severity.DEBUG
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.expect

class InMemoryDataStoreScanTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<Log>>()
    private var lowestVersion = ULong.MAX_VALUE

    private val logs = arrayOf(
        Log("Something happened", timestamp = DateTime(2018, 11, 14, 11, 22, 33, 40)),
        Log("Something else happened", DEBUG, DateTime(2018, 11, 14, 12, 0, 0, 0)),
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
    fun executeSimpleScanRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(startKey = keys[2])
        )

        expect(3) { scanResponse.values.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.values[0].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[2].let {
            expect(logs[0]) { it.values }
            expect(keys[0]) { it.key }
        }
    }

    @Test
    fun executeSimpleScanWithAggregationRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                startKey = keys[2],
                aggregations = Aggregations(
                    "last" to Max(
                        Log { timestamp::ref }
                    ),
                    "first" to Min(
                        Log { timestamp::ref }
                    )
                )
            )
        )

        expect(3) { scanResponse.values.size }

        expect(
            AggregationsResponse(
                "last" to MaxResponse(
                    Log { timestamp::ref }, DateTime(2018, 11, 14, 12, 33, 22, 111)
                ),
                "first" to MinResponse(
                    Log { timestamp::ref }, DateTime(2018, 11, 14, 11, 22, 33, 40)
                )
            )
        ) {
            scanResponse.aggregations
        }
    }

    @Test
    fun executeSimpleScanRequestReverseOrder() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(startKey = keys[2], order = descending)
        )

        expect(3) { scanResponse.values.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.values[0].let {
            expect(logs[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[2].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    @Test
    fun executeScanRequestWithLimit() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(startKey = keys[2], limit = 1u)
        )

        expect(1) { scanResponse.values.size }

        // Mind that Log is sorted in reverse so it goes back in time going forward
        scanResponse.values[0].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    @Test
    fun executeScanRequestWithToVersion() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(startKey = keys[2], toVersion = lowestVersion - 1uL)
        )

        expect(0) { scanResponse.values.size }
    }

    @Test
    fun executeScanRequestWithSelect() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                startKey = keys[2],
                select = Log.graph {
                    listOf(
                        timestamp,
                        severity
                    )
                }
            )
        )

        expect(3) { scanResponse.values.size }

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
    fun executeSimpleScanFilterRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = Equals(
                    severity.ref() with DEBUG
                )
            )
        )

        expect(1) { scanResponse.values.size }

        scanResponse.values[0].let {
            expect(logs[1]) { it.values }
            expect(keys[1]) { it.key }
        }
    }

    @Test
    fun executeSimpleScanFilterExactMatchRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = Equals(
                    severity.ref() with INFO,
                    timestamp.ref() with DateTime(2018, 11, 14, 11, 22, 33, 40),
                    message.ref() with "Something happened"
                )
            )
        )

        expect(1) { scanResponse.values.size }

        scanResponse.values[0].let {
            expect(logs[0]) { it.values }
            expect(keys[0]) { it.key }
        }
    }

    @Test
    fun executeSimpleScanFilterExactWrongMatchRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = Equals(
                    severity.ref() with INFO,
                    timestamp.ref() with DateTime(2018, 11, 14, 11, 22, 33, 40),
                    message.ref() with "WRONG happened"
                )
            )
        )

        expect(0) { scanResponse.values.size }
    }
}

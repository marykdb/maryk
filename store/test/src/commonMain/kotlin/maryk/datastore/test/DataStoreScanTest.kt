package maryk.datastore.test

import kotlinx.datetime.LocalDateTime
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.Max
import maryk.core.aggregations.metric.MaxResponse
import maryk.core.aggregations.metric.Min
import maryk.core.aggregations.metric.MinResponse
import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Order.Companion.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.Log
import maryk.test.models.Log.Properties.message
import maryk.test.models.Log.Properties.severity
import maryk.test.models.Log.Properties.timestamp
import maryk.test.models.Severity.DEBUG
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.expect

class DataStoreScanTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<Log>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleScanRequest" to ::executeSimpleScanRequest,
        "executeSimpleScanWithAggregationRequest" to ::executeSimpleScanWithAggregationRequest,
        "executeSimpleScanRequestReverseOrder" to ::executeSimpleScanRequestReverseOrder,
        "executeScanRequestWithLimit" to ::executeScanRequestWithLimit,
        "executeScanRequestWithToVersion" to ::executeScanRequestWithToVersion,
        "executeScanRequestWithSelect" to ::executeScanRequestWithSelect,
        "executeSimpleScanFilterRequest" to ::executeSimpleScanFilterRequest,
        "executeSimpleScanFilterExactMatchRequest" to ::executeSimpleScanFilterExactMatchRequest,
        "executeSimpleScanFilterExactWrongMatchRequest" to ::executeSimpleScanFilterExactWrongMatchRequest
    )

    private val logs = arrayOf(
        Log("Something happened", timestamp = LocalDateTime(2018, 11, 14, 11, 22, 33, 40000000)),
        Log("Something else happened", DEBUG, LocalDateTime(2018, 11, 14, 12, 0, 0, 0)),
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

    private suspend fun executeSimpleScanRequest() {
        val scanResponse = dataStore.execute(
            Log.scan(startKey = keys[2])
        )

        expect(3) { scanResponse.values.size }

        // Mind that Log is sorted in reverse, so it goes back in time going forward
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

    private suspend fun executeSimpleScanWithAggregationRequest() {
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
                    Log { timestamp::ref }, LocalDateTime(2018, 11, 14, 12, 33, 22, 111000000)
                ),
                "first" to MinResponse(
                    Log { timestamp::ref }, LocalDateTime(2018, 11, 14, 11, 22, 33, 40000000)
                )
            )
        ) {
            scanResponse.aggregations
        }
    }

    private suspend fun executeSimpleScanRequestReverseOrder() {
        val scanResponse = dataStore.execute(
            Log.scan(startKey = keys[2], order = descending)
        )

        expect(2) { scanResponse.values.size }

        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.values[0].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[1].let {
            expect(logs[3]) { it.values }
            expect(keys[3]) { it.key }
        }
    }

    private suspend fun executeScanRequestWithLimit() {
        val scanResponse = dataStore.execute(
            Log.scan(startKey = keys[2], limit = 1u)
        )

        expect(1) { scanResponse.values.size }

        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.values[0].let {
            expect(logs[2]) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    private suspend fun executeScanRequestWithToVersion() {
        if (dataStore.keepAllVersions) {
            val scanResponse = dataStore.execute(
                Log.scan(startKey = keys[2], toVersion = lowestVersion - 1uL)
            )

            expect(0) { scanResponse.values.size }
        } else {
            assertFailsWith<RequestException> {
                dataStore.execute(
                    Log.scan(startKey = keys[2], toVersion = lowestVersion - 1uL)
                )
            }
        }
    }

    private suspend fun executeScanRequestWithSelect() {
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

        // Mind that Log is sorted in reverse, so it goes back in time going forward
        scanResponse.values[0].let {
            expect(
                Log.values {
                    mapNonNulls(
                        this.severity with INFO,
                        this.timestamp with LocalDateTime(2018, 11, 14, 12, 33, 22, 111000000)
                    )
                }
            ) { it.values }
            expect(keys[2]) { it.key }
        }
    }

    private suspend fun executeSimpleScanFilterRequest() {
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

    private suspend fun executeSimpleScanFilterExactMatchRequest() {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = Equals(
                    severity.ref() with INFO,
                    timestamp.ref() with LocalDateTime(2018, 11, 14, 11, 22, 33, 40000000),
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

    private suspend fun executeSimpleScanFilterExactWrongMatchRequest() {
        val scanResponse = dataStore.execute(
            Log.scan(
                where = Equals(
                    severity.ref() with INFO,
                    timestamp.ref() with LocalDateTime(2018, 11, 14, 11, 22, 33, 40000000),
                    message.ref() with "WRONG happened"
                )
            )
        )

        expect(0) { scanResponse.values.size }
    }
}

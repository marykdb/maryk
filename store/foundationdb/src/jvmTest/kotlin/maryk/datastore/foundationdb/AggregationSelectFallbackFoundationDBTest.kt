package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.Max
import maryk.core.aggregations.metric.MaxResponse
import maryk.core.models.graph
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class AggregationSelectFallbackFoundationDBTest {
    @Test
    fun getAggregationReadsNonSelectedField() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "get-aggregation-select-fallback", Uuid.random().toString()),
            dataModelsById = mapOf(1u to Log),
            keepAllVersions = true,
        )

        try {
            val first = Log("first", timestamp = LocalDateTime(2024, 1, 1, 10, 0))
            val second = Log("second", timestamp = LocalDateTime(2024, 1, 1, 11, 0))
            val statuses = store.execute(Log.add(first, second)).statuses.map { assertIs<AddSuccess<Log>>(it) }

            val response = store.execute(
                Log.get(
                    statuses[0].key,
                    statuses[1].key,
                    select = Log.graph { listOf(Log.message) },
                    aggregations = Aggregations(
                        "last" to Max(Log { timestamp::ref })
                    )
                )
            )

            assertEquals(2, response.values.size)
            assertEquals(
                AggregationsResponse(
                    "last" to MaxResponse(
                        Log { timestamp::ref },
                        LocalDateTime(2024, 1, 1, 11, 0)
                    )
                ),
                response.aggregations
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun historicGetAggregationReadsNonSelectedField() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-get-aggregation-select-fallback", Uuid.random().toString()),
            dataModelsById = mapOf(1u to Log),
            keepAllVersions = true,
        )

        try {
            val first = Log("first", timestamp = LocalDateTime(2024, 1, 1, 10, 0))
            val second = Log("second", timestamp = LocalDateTime(2024, 1, 1, 11, 0))
            val statuses = store.execute(Log.add(first, second)).statuses.map { assertIs<AddSuccess<Log>>(it) }

            val response = store.execute(
                Log.get(
                    statuses[0].key,
                    statuses[1].key,
                    select = Log.graph { listOf(Log.message) },
                    toVersion = statuses[1].version,
                    aggregations = Aggregations(
                        "last" to Max(Log { timestamp::ref })
                    )
                )
            )

            assertEquals(2, response.values.size)
            assertEquals(
                AggregationsResponse(
                    "last" to MaxResponse(
                        Log { timestamp::ref },
                        LocalDateTime(2024, 1, 1, 11, 0)
                    )
                ),
                response.aggregations
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun scanAggregationReadsNonSelectedField() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "scan-aggregation-select-fallback", Uuid.random().toString()),
            dataModelsById = mapOf(1u to Log),
            keepAllVersions = true,
        )

        try {
            store.execute(
                Log.add(
                    Log("first", timestamp = LocalDateTime(2024, 1, 1, 10, 0)),
                    Log("second", timestamp = LocalDateTime(2024, 1, 1, 11, 0))
                )
            ).statuses.forEach { assertIs<AddSuccess<Log>>(it) }

            val response = store.execute(
                Log.scan(
                    select = Log.graph { listOf(Log.message) },
                    aggregations = Aggregations(
                        "last" to Max(Log { timestamp::ref })
                    )
                )
            )

            assertEquals(2, response.values.size)
            assertEquals(
                AggregationsResponse(
                    "last" to MaxResponse(
                        Log { timestamp::ref },
                        LocalDateTime(2024, 1, 1, 11, 0)
                    )
                ),
                response.aggregations
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun historicScanAggregationReadsNonSelectedField() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-scan-aggregation-select-fallback", Uuid.random().toString()),
            dataModelsById = mapOf(1u to Log),
            keepAllVersions = true,
        )

        try {
            val statuses = store.execute(
                Log.add(
                    Log("first", timestamp = LocalDateTime(2024, 1, 1, 10, 0)),
                    Log("second", timestamp = LocalDateTime(2024, 1, 1, 11, 0))
                )
            ).statuses.map { assertIs<AddSuccess<Log>>(it) }

            val response = store.execute(
                Log.scan(
                    select = Log.graph { listOf(Log.message) },
                    toVersion = statuses[1].version,
                    aggregations = Aggregations(
                        "last" to Max(Log { timestamp::ref })
                    )
                )
            )

            assertEquals(2, response.values.size)
            assertEquals(
                AggregationsResponse(
                    "last" to MaxResponse(
                        Log { timestamp::ref },
                        LocalDateTime(2024, 1, 1, 11, 0)
                    )
                ),
                response.aggregations
            )
        } finally {
            store.close()
        }
    }
}

package maryk.datastore.foundationdb

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FoundationDBClusterUpdateLogConfigurationTest {
    @Test
    fun retentionRejectsNonPositiveAndNonFiniteDurations() {
        listOf(Duration.ZERO, (-1).milliseconds, Duration.INFINITE).forEach { retention ->
            assertFailsWith<IllegalArgumentException> {
                FoundationDBClusterUpdateLogConfiguration(clusterUpdateLogRetention = retention)
            }
        }
    }

    @Test
    fun pollIntervalRejectsNonPositiveAndNonFiniteDurations() {
        listOf(Duration.ZERO, (-1).milliseconds, Duration.INFINITE).forEach { pollInterval ->
            assertFailsWith<IllegalArgumentException> {
                FoundationDBClusterUpdateLogConfiguration(clusterUpdateLogPollInterval = pollInterval)
            }
        }
    }

    @Test
    fun batchSizeRejectsNegativeZeroAndAboveMaximumValues() {
        listOf(-1, 0, 10_001).forEach { batchSize ->
            assertFailsWith<IllegalArgumentException> {
                FoundationDBClusterUpdateLogConfiguration(clusterUpdateLogBatchSize = batchSize)
            }
        }
    }

    @Test
    fun shardCountRejectsNegativeZeroAndAboveMaximumValues() {
        listOf(-1, 0, 1_025).forEach { shardCount ->
            assertFailsWith<IllegalArgumentException> {
                FoundationDBClusterUpdateLogConfiguration(clusterUpdateLogShardCount = shardCount)
            }
        }
    }

    @Test
    fun maximumConfigurationFitsCursorAllocationLimit() {
        FoundationDBClusterUpdateLogConfiguration(
            clusterUpdateLogShardCount = 1_024,
            clusterUpdateLogBatchSize = 10_000,
        ).validateCursorCapacity(modelCount = 64)
    }

    @Test
    fun cursorCapacityRejectsMultiplicationThatWouldOverflowAnIntArraySize() {
        assertFailsWith<IllegalArgumentException> {
            FoundationDBClusterUpdateLogConfiguration(
                clusterUpdateLogShardCount = 1_024,
            ).validateCursorCapacity(modelCount = Int.MAX_VALUE)
        }
    }
}

package maryk.datastore.foundationdb

import kotlin.test.Test
import kotlin.test.assertFailsWith

class FoundationDBClusterUpdateLogConfigurationTest {
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

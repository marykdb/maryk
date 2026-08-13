package maryk.datastore.foundationdb

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal const val MAX_CLUSTER_UPDATE_LOG_SHARD_COUNT = 1_024
internal const val MAX_CLUSTER_UPDATE_LOG_BATCH_SIZE = 10_000
private const val MAX_CLUSTER_UPDATE_LOG_CURSOR_COUNT = 65_536L

data class FoundationDBClusterUpdateLogConfiguration(
    val enableClusterUpdateLog: Boolean = false,
    val clusterUpdateLogConsumerId: String? = null,
    val clusterUpdateLogOriginId: String? = null,
    val clusterUpdateLogShardCount: Int = 64,
    val clusterUpdateLogRetention: Duration = 60.minutes,
    val clusterUpdateLogBatchSize: Int = 256,
    val clusterUpdateLogPollInterval: Duration = 250.milliseconds,
) {
    init {
        require(clusterUpdateLogShardCount in 1..MAX_CLUSTER_UPDATE_LOG_SHARD_COUNT) {
            "clusterUpdateLogShardCount should be between 1 and $MAX_CLUSTER_UPDATE_LOG_SHARD_COUNT but was $clusterUpdateLogShardCount"
        }
        require(clusterUpdateLogBatchSize in 1..MAX_CLUSTER_UPDATE_LOG_BATCH_SIZE) {
            "clusterUpdateLogBatchSize should be between 1 and $MAX_CLUSTER_UPDATE_LOG_BATCH_SIZE but was $clusterUpdateLogBatchSize"
        }
        require(clusterUpdateLogRetention.isPositive() && clusterUpdateLogRetention.isFinite()) {
            "clusterUpdateLogRetention should be positive and finite but was $clusterUpdateLogRetention"
        }
        require(clusterUpdateLogPollInterval.isPositive() && clusterUpdateLogPollInterval.isFinite()) {
            "clusterUpdateLogPollInterval should be positive and finite but was $clusterUpdateLogPollInterval"
        }
    }

    /** Validates the cursor array capacity before the cluster log tailer allocates it. */
    internal fun validateCursorCapacity(modelCount: Int) {
        require(modelCount >= 0) { "modelCount should not be negative but was $modelCount" }

        val cursorCount = clusterUpdateLogShardCount.toLong() * modelCount.toLong()
        require(cursorCount <= MAX_CLUSTER_UPDATE_LOG_CURSOR_COUNT) {
            "cluster update log cursor count $cursorCount exceeds maximum $MAX_CLUSTER_UPDATE_LOG_CURSOR_COUNT"
        }
    }
}

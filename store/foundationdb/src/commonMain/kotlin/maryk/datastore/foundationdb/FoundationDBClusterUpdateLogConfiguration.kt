package maryk.datastore.foundationdb

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

data class FoundationDBClusterUpdateLogConfiguration(
    val enableClusterUpdateLog: Boolean = false,
    val clusterUpdateLogConsumerId: String? = null,
    val clusterUpdateLogOriginId: String? = null,
    val clusterUpdateLogShardCount: Int = 64,
    val clusterUpdateLogRetention: Duration = 60.minutes,
    val clusterUpdateLogBatchSize: Int = 256,
    val clusterUpdateLogPollInterval: Duration = 250.milliseconds,
)

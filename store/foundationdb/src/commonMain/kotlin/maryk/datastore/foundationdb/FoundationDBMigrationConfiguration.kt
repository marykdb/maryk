package maryk.datastore.foundationdb

data class FoundationDBMigrationLeaseConfiguration(
    val migrationLeaseTimeoutMs: Long = 30_000L,
    val migrationLeaseHeartbeatMs: Long = 10_000L,
)

package maryk.datastore.foundationdb

data class FoundationDBMigrationLeaseConfiguration(
    val migrationLeaseTimeoutMs: Long = 30_000L,
    val migrationLeaseHeartbeatMs: Long = 10_000L,
) {
    init {
        require(migrationLeaseTimeoutMs > 0L) {
            "migrationLeaseTimeoutMs should be positive but was $migrationLeaseTimeoutMs"
        }
        require(migrationLeaseHeartbeatMs > 0L) {
            "migrationLeaseHeartbeatMs should be positive but was $migrationLeaseHeartbeatMs"
        }
        require(migrationLeaseHeartbeatMs < migrationLeaseTimeoutMs) {
            "migrationLeaseHeartbeatMs should be less than migrationLeaseTimeoutMs"
        }
    }
}

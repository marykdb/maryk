package maryk.core.models.migration

interface MigrationLease {
    suspend fun tryAcquire(modelId: UInt, migrationId: String): Boolean
    suspend fun release(modelId: UInt, migrationId: String)
}

object NoopMigrationLease : MigrationLease {
    override suspend fun tryAcquire(modelId: UInt, migrationId: String): Boolean = true
    override suspend fun release(modelId: UInt, migrationId: String) = Unit
}

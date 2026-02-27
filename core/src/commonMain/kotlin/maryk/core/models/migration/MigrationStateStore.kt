package maryk.core.models.migration

interface MigrationStateStore {
    suspend fun read(modelId: UInt): MigrationState?
    suspend fun write(modelId: UInt, state: MigrationState)
    suspend fun clear(modelId: UInt)
}

package maryk.datastore.shared.migration

import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationRuntimeStatus

data class MigrationAdminSnapshot(
    val statuses: Map<UInt, MigrationRuntimeStatus>,
    val metrics: Map<UInt, MigrationMetrics>,
)

/** Common operational surface implemented by stores with managed migrations. */
interface MigrationAdmin {
    suspend fun getMigrationStatuses(): Map<UInt, MigrationRuntimeStatus>
    suspend fun getMigrationMetrics(): Map<UInt, MigrationMetrics>
    suspend fun getMigrationSnapshot(): MigrationAdminSnapshot =
        MigrationAdminSnapshot(getMigrationStatuses(), getMigrationMetrics())
    suspend fun requestMigrationPause(modelId: UInt): Boolean
    suspend fun requestMigrationResume(modelId: UInt): Boolean
    suspend fun requestMigrationCancel(modelId: UInt, reason: String = "Canceled by operator"): Boolean
}

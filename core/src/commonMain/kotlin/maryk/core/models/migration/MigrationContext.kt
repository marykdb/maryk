package maryk.core.models.migration

/**
 * Context passed to MigrationHandler.
 */
data class MigrationContext<DS>(
    val store: DS,
    val storedDataModel: StoredRootDataModelDefinition,
    val newDataModel: NewRootDataModelDefinition,
    val migrationStatus: MigrationStatus.NeedsMigration,
    val previousState: MigrationState?,
    val attempt: UInt,
)

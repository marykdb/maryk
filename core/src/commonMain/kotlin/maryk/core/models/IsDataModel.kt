package maryk.core.models

import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.OnlySafeAdds
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.models.migration.checkProperties
import maryk.core.properties.IsPropertyDefinitions

/** A DataModel which holds properties and can be validated */
interface IsDataModel<P : IsPropertyDefinitions> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: P

    /**
     * Checks if a migration is needed between [storedDataModel] and current model and returns a status
     * indicating if the models are compatible.
     * Pass a [migrationReasons] if this method is overridden
     */
    fun isMigrationNeeded(
        storedDataModel: IsDataModel<*>,
        migrationReasons: MutableList<String> = mutableListOf()
    ): MigrationStatus {
        @Suppress("UNCHECKED_CAST")
        val hasNewProperties = checkProperties(storedDataModel as IsDataModel<P>) {
            migrationReasons += it
        }

        return when {
            migrationReasons.isNotEmpty() -> NeedsMigration(storedDataModel, migrationReasons, null)
            hasNewProperties -> OnlySafeAdds
            else -> UpToDate
        }
    }
}

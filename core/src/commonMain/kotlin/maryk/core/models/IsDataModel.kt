package maryk.core.models

import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.OnlySafeAdds
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.models.migration.checkProperties
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference

/** A DataModel which holds properties and can be validated */
interface IsDataModel<P : IsPropertyDefinitions> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: P

    /**
     * Get property reference fetcher of this DataModel with [referenceGetter]
     * Optionally pass an already resolved [parent]
     * For Strongly typed reference notation
     */
    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(this.properties)(parent)

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

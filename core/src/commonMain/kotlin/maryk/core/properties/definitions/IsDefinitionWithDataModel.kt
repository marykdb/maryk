package maryk.core.properties.definitions

import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.properties.IsObjectPropertyDefinitions

/** Interface for property definitions defined by data model of [DM]. */
interface IsDefinitionWithDataModel<out DM : IsObjectPropertyDefinitions<*>> {
    val dataModel: DM

    /**
     * Checks compatibility of the data model contained with the data model in [definition]
     * If it does not match the reason is added with [addIncompatibilityReason]
     */
    fun compatibleWithDefinitionWithDataModel(
        definition: IsDefinitionWithDataModel<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ) = when (val migrationStatus = this.dataModel.Model.isMigrationNeeded(definition.dataModel.Model)) {
        is NeedsMigration -> {
            for (reason in migrationStatus.migrationReasons) {
                addIncompatibilityReason?.invoke("DataModel not matching: $reason")
            }
            false
        }
        else -> true // All other statuses are safe for
    }
}

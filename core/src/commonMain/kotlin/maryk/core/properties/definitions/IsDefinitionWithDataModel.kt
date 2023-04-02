package maryk.core.properties.definitions

import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.IsStorableDataModel
import maryk.core.models.IsTypedDataModel

/** Interface for property definitions defined by data model of [DM]. */
interface IsDefinitionWithDataModel<out DM : IsTypedDataModel<*>> {
    val dataModel: DM

    /**
     * Checks compatibility of the data model contained with the data model in [definition]
     * If it does not match the reason is added with [addIncompatibilityReason]
     */
    fun compatibleWithDefinitionWithDataModel(
        definition: IsDefinitionWithDataModel<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        val comparisonDataModel = definition.dataModel as? IsStorableDataModel

        if (comparisonDataModel == null) {
            addIncompatibilityReason?.invoke("DataModel in definition not Serializable")
            return false
        }

        return when (val migrationStatus = (this.dataModel as? IsStorableDataModel)?.isMigrationNeeded(definition.dataModel as IsStorableDataModel)) {
            null -> {
                addIncompatibilityReason?.invoke("current DataModel not Serializable")
                false
            }
            is NeedsMigration -> {
                for (reason in migrationStatus.migrationReasons) {
                    addIncompatibilityReason?.invoke("DataModel not matching: $reason")
                }
                false
            }
            else -> true // All other statuses are safe for
        }
    }
}

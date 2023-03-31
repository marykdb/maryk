package maryk.core.properties.definitions

import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.properties.IsSerializableModel
import maryk.core.properties.IsTypedPropertyDefinitions

/** Interface for property definitions defined by data model of [DM]. */
interface IsDefinitionWithDataModel<out DM : IsTypedPropertyDefinitions<*>> {
    val dataModel: DM

    /**
     * Checks compatibility of the data model contained with the data model in [definition]
     * If it does not match the reason is added with [addIncompatibilityReason]
     */
    fun compatibleWithDefinitionWithDataModel(
        definition: IsDefinitionWithDataModel<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        val comparisonDataModel = definition.dataModel as? IsSerializableModel

        if (comparisonDataModel == null) {
            addIncompatibilityReason?.invoke("DataModel in definition not Serializable")
            return false
        }

        return when (val migrationStatus = (this.dataModel as? IsSerializableModel)?.isMigrationNeeded(definition.dataModel as IsSerializableModel)) {
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

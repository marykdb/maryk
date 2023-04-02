package maryk.core.properties

import maryk.core.models.definitions.IsNamedDataModelDefinition
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.checkProperties

interface IsStorableModel: IsPropertyDefinitions {
    val Model: IsNamedDataModelDefinition<out IsPropertyDefinitions>

    /**
     * Checks if a migration is needed between [storedDataModel] and current model and returns a status
     * indicating if the models are compatible.
     * Pass a [migrationReasons] if this method is overridden
     */
    fun isMigrationNeeded(
        storedDataModel: IsStorableModel,
        migrationReasons: MutableList<String> = mutableListOf()
    ): MigrationStatus {
        if (storedDataModel.Model.name != this.Model.name) {
            migrationReasons += "Names of models did not match: ${storedDataModel.Model.name} -> ${this.Model.name}"
        }

        val hasNewProperties = this.checkProperties(storedDataModel) {
            migrationReasons += it
        }

        return when {
            migrationReasons.isNotEmpty() -> MigrationStatus.NeedsMigration(storedDataModel, migrationReasons, null)
            hasNewProperties -> MigrationStatus.OnlySafeAdds
            else -> MigrationStatus.UpToDate
        }
    }
}

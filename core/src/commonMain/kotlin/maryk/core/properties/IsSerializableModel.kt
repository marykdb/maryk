package maryk.core.properties

import maryk.core.models.IsNamedDataModel
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.checkProperties

interface IsSerializableModel: IsPropertyDefinitions {
    override val Model: IsNamedDataModel<*>

    /**
     * Checks if a migration is needed between [storedDataModel] and current model and returns a status
     * indicating if the models are compatible.
     * Pass a [migrationReasons] if this method is overridden
     */
    fun isMigrationNeeded(
        storedDataModel: IsSerializableModel,
        migrationReasons: MutableList<String> = mutableListOf()
    ): MigrationStatus {
        if (storedDataModel.Model.name != this.Model.name) {
            migrationReasons += "Names of models did not match: ${storedDataModel.Model.name} -> ${this.Model.name}"
        }

        val hasNewProperties = this.checkProperties(storedDataModel) {
            migrationReasons += it
        }

        return when {
            migrationReasons.isNotEmpty() -> MigrationStatus.NeedsMigration(storedDataModel.Model, migrationReasons, null)
            hasNewProperties -> MigrationStatus.OnlySafeAdds
            else -> MigrationStatus.UpToDate
        }
    }
}

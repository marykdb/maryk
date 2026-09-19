package maryk.core.models

import maryk.core.models.definitions.IsDataModelDefinition
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.checkProperties

/**
 * Interface for any DataModel which can be stored.
 * Can be validated against a stored model to check if migration is needed.
 */
interface IsStorableDataModel<DO: Any>: IsTypedDataModel<DO> {
    val Meta: IsDataModelDefinition

    /**
     * Checks if a migration is needed between [storedDataModel] and current model and returns a status
     * indicating if the models are compatible.
     * Pass a [migrationReasons] if this method is overridden
     */
    fun isMigrationNeeded(
        storedDataModel: IsStorableDataModel<*>,
        checkedDataModelNames: MutableList<String>? = mutableListOf(),
        migrationReasons: MutableList<String> = mutableListOf(),
    ): MigrationStatus {
        if (checkedDataModelNames?.contains(this.Meta.name) == true) {
            return MigrationStatus.AlreadyProcessed
        }
        checkedDataModelNames?.add(this.Meta.name)

        if (storedDataModel.Meta.name != this.Meta.name) {
            migrationReasons += "Names of models did not match: ${storedDataModel.Meta.name} -> ${this.Meta.name}"
        }

        val hasNewProperties = this.checkProperties(storedDataModel, checkedDataModelNames) {
            migrationReasons += it
        }

        return when {
            migrationReasons.isNotEmpty() -> MigrationStatus.NeedsMigration(storedDataModel, migrationReasons, null)
            hasNewProperties -> MigrationStatus.OnlySafeAdds(storedDataModel)
            else -> MigrationStatus.UpToDate
        }
    }
}

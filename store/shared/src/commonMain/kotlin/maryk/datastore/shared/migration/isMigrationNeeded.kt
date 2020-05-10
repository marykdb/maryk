package maryk.datastore.shared.migration

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.shared.migration.MigrationStatus.NeedsMigration
import maryk.datastore.shared.migration.MigrationStatus.OnlySafeAdds
import maryk.datastore.shared.migration.MigrationStatus.UpToDate

fun <P: PropertyDefinitions> IsRootValuesDataModel<P>.isMigrationNeeded(storedDataModel: IsRootValuesDataModel<*>): MigrationStatus {
    if (storedDataModel.version.major != this.version.major) {
        return NeedsMigration(storedDataModel, null)
    }

    if (storedDataModel.keyDefinition !== this.keyDefinition) {
        return NeedsMigration(storedDataModel, null)
    }
    return when (storedDataModel.version) {
        this.version -> UpToDate
        else -> OnlySafeAdds
    }
}

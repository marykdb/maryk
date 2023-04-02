package maryk.core.models.migration

import maryk.core.properties.IsStorableModel
import maryk.core.properties.definitions.index.IsIndexable

sealed class MigrationStatus {
    /** There is no existing model so the model is to be added as new. */
    object NewModel: MigrationStatus()
    /** The model is up-to-date and needs no migration */
    object UpToDate: MigrationStatus()
    /** The model only has additions which do not need a migration */
    object OnlySafeAdds: MigrationStatus()
    /** The model contains new indexes on existing properties, which need to be indexed */
    class NewIndicesOnExistingProperties(val indicesToIndex: List<IsIndexable>): MigrationStatus()
    /** The model is incompatible with the stored version and needs a migration */
    class NeedsMigration(
        val storedDataModel: IsStorableModel,
        val migrationReasons: List<String>,
        val indicesToIndex: List<IsIndexable>?
    ): MigrationStatus() {
        override fun toString() = "NeedsMigration:\n\t${migrationReasons.joinToString("\n\t")}"
    }
}

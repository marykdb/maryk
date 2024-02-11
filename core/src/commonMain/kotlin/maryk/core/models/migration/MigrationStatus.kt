package maryk.core.models.migration

import maryk.core.models.IsStorableDataModel
import maryk.core.properties.definitions.index.IsIndexable

interface ModelChangeStatus {
    val storedDataModel: IsStorableDataModel<*>
}

sealed class MigrationStatus {
    /** There is no existing model so the model is to be added as new. */
    object NewModel: MigrationStatus()

    /** The model has already been processed and migration depends on the other result */
    object AlreadyProcessed: MigrationStatus()

    /** The model is up-to-date and needs no migration */
    object UpToDate: MigrationStatus()

    /** The model only has additions which do not need a migration */
    class OnlySafeAdds(
        override val storedDataModel: IsStorableDataModel<*>,
    ): MigrationStatus(), ModelChangeStatus

    /** The model contains new indexes on existing properties, which need to be indexed */
    class NewIndicesOnExistingProperties(
        override val storedDataModel: IsStorableDataModel<*>,
        val indicesToIndex: List<IsIndexable>
    ): MigrationStatus(), ModelChangeStatus

    /** The model is incompatible with the stored version and needs a migration */
    class NeedsMigration(
        override val storedDataModel: IsStorableDataModel<*>,
        val migrationReasons: List<String>,
        val indicesToIndex: List<IsIndexable>?
    ): MigrationStatus(), ModelChangeStatus {
        override fun toString() = "NeedsMigration:\n\t${migrationReasons.joinToString("\n\t")}"
    }
}

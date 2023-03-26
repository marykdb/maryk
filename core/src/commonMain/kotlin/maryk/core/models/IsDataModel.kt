package maryk.core.models

import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.OnlySafeAdds
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.models.migration.checkProperties
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues

/** A DataModel which holds properties and can be validated */
interface IsDataModel<P : IsPropertyDefinitions> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: P

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

    /**
     * Checks if the DataModel is compatible with [propertyReference]
     * This is useful to test if reference is compatible after migration with already stored model.
     * This result can be used to know if an index has to be indexed with existing values.
     */
    fun compatibleWithReference(propertyReference: AnyPropertyReference): Boolean {
        val unwrappedReferences = propertyReference.unwrap()

        var model: IsDataModel<*> = this

        for (reference in unwrappedReferences) {
            if (reference is IsPropertyReferenceForValues<*, *, *, *>) {
                when(val storedPropertyDefinition = model.properties[reference.index]) {
                    null -> return false
                    else -> {
                        val propertyDefinition = reference.propertyDefinition

                        if (propertyDefinition is IsEmbeddedDefinition<*>) {
                            if (storedPropertyDefinition !is IsEmbeddedDefinition<*>) {
                                return false // Types are not matching
                            } else {
                                model = storedPropertyDefinition.dataModel.Model
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}

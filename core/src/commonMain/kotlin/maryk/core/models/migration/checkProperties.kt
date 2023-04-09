package maryk.core.models.migration

import maryk.core.models.AbstractDataModel
import maryk.core.models.IsStorableDataModel
import maryk.core.models.definitions.IsValuesDataModelDefinition
import maryk.lib.synchronizedIteration

/**
 * Check properties of data model against [storedDataModel]
 *
 * Properties available on both will be checked if they are compatible
 * Properties only on new model will be checked if they are not required since old values cannot
 * be present without migration.
 * Properties only on stored data model will be checked if they are available on the reservedIndices and
 * names so they cannot be used for any future model without acknowledgement in a migration.
 */
internal fun IsStorableDataModel<*>.checkProperties(
    storedDataModel: IsStorableDataModel<*>,
    handleMigrationReason: (String) -> Unit
): Boolean {
    var hasNewProperties = false

    @Suppress("UNCHECKED_CAST")
    synchronizedIteration(
        (this as AbstractDataModel<Any>).iterator(),
        (storedDataModel as AbstractDataModel<Any>).iterator(),
        { newValue, storedValue ->
            newValue.index compareTo storedValue.index
        },
        { newProp, storedProp ->
            newProp.compatibleWith(storedProp, handleMigrationReason)
        },
        { newProp ->
            hasNewProperties = true
            if (newProp.required) {
                handleMigrationReason("Required property ${newProp.index}:${newProp.name}")
            }
        },
        { storedProp ->
            val model = this.Meta
            if (model is IsValuesDataModelDefinition) {
                if (model.reservedIndices?.contains(storedProp.index) != true) {
                    handleMigrationReason("Property with index ${storedProp.index} is not present in new model. Please add it to `reservedIndices` or add back the property to avoid this exception.")
                }
                val allNames = storedProp.alternativeNames?.let { it + storedProp.name } ?: setOf(storedProp.name)
                if (model.reservedNames?.containsAll(allNames) != true) {
                    handleMigrationReason("Property with name(s) `${allNames.joinToString()}` is not present in new model. Please add it to `reservedNames` or add back the property to avoid this exception.")
                }
            }
        }
    )
    return hasNewProperties
}

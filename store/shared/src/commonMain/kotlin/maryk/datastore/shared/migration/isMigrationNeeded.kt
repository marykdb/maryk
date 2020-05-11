package maryk.datastore.shared.migration

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.datastore.shared.migration.MigrationStatus.NeedsMigration
import maryk.datastore.shared.migration.MigrationStatus.OnlySafeAdds
import maryk.datastore.shared.migration.MigrationStatus.UpToDate

fun <P: PropertyDefinitions> IsRootValuesDataModel<P>.isMigrationNeeded(storedDataModel: IsRootValuesDataModel<*>): MigrationStatus {
    val migrationReasons = mutableListOf<String>()

    if (storedDataModel.version.major != this.version.major) {
        migrationReasons += "Major version was increased: ${storedDataModel.version} -> ${this.version}"
    }

    if (storedDataModel.keyDefinition !== this.keyDefinition) {
        migrationReasons += "Key definition was not the same"
    }

    val hasNewProperties = checkProperties(storedDataModel) {
        migrationReasons += it
    }

    return when {
        migrationReasons.isNotEmpty() -> NeedsMigration(storedDataModel, migrationReasons, null)
        hasNewProperties -> OnlySafeAdds
        else -> UpToDate
    }
}

/**
 * Check properties of data model against [storedDataModel]
 *
 * Properties available on both will be checked if they are compatible
 * Properties only on new model will be checked if they are not required since old values cannot
 * be present without migration.
 * Properties only on stored data model will be checked if they are available on the reservedIndices and
 * names so they cannot be used for any future model without acknowledgement in a migration.
 */
private fun <P : PropertyDefinitions> IsRootValuesDataModel<P>.checkProperties(
    storedDataModel: IsRootValuesDataModel<*>,
    handleMigrationReason: (String) -> Unit
): Boolean {
    var hasNewProperties = false
    val newIterator = this.properties.iterator()
    val storedIterator = storedDataModel.properties.iterator()

    var newProperty: AnyDefinitionWrapper? = newIterator.next()
    var storedProperty: AnyDefinitionWrapper? = storedIterator.next()

    fun processNew(newProp: AnyDefinitionWrapper) {
        hasNewProperties = true

        if (newProp.required) {
            handleMigrationReason("Required property ${newProp.index}:${newProp.name}")
        }

        newProperty = if (newIterator.hasNext()) newIterator.next() else null
    }

    fun processStored(storedProp: AnyDefinitionWrapper) {
        if (this.reservedIndices?.contains(storedProp.index) != true) {
            handleMigrationReason("Property with index ${storedProp.index} is not present in new model. Please add it to `reservedIndices` or add back the property to avoid this exception.")
        }
        val allNames = storedProp.alternativeNames?.let { it + storedProp.name } ?: setOf(storedProp.name)
        if (this.reservedNames?.containsAll(allNames) != true) {
            handleMigrationReason("Property with name(s) `${allNames.joinToString()}` is not present in new model. Please add it to `reservedNames` or add back the property to avoid this exception.")
        }
        storedProperty = if (storedIterator.hasNext()) storedIterator.next() else null
    }

    fun compareNewWithStored() {
        storedProperty = if (storedIterator.hasNext()) storedIterator.next() else null
        newProperty = if (newIterator.hasNext()) newIterator.next() else null
    }

    while (newProperty != null || storedProperty != null) {
        val newProp = newProperty
        val storedProp = storedProperty
        when {
            newProp == null ->
                processStored(storedProp!!)
            storedProp == null ->
                processNew(newProp)
            newProp.index == storedProp.index ->
                compareNewWithStored()
            newProp.index < storedProp.index ->
                if (newIterator.hasNext()) processNew(newProp) else processStored(storedProp)
            newProp.index > storedProp.index ->
                if (storedIterator.hasNext()) processStored(storedProp) else processNew(newProp)
        }
    }
    return hasNewProperties
}

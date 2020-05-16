package maryk.core.models.migration

import maryk.core.models.IsDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper

/**
 * Check properties of data model against [storedDataModel]
 *
 * Properties available on both will be checked if they are compatible
 * Properties only on new model will be checked if they are not required since old values cannot
 * be present without migration.
 * Properties only on stored data model will be checked if they are available on the reservedIndices and
 * names so they cannot be used for any future model without acknowledgement in a migration.
 */
internal fun <P : IsPropertyDefinitions> IsDataModel<P>.checkProperties(
    storedDataModel: IsDataModel<P>,
    handleMigrationReason: (String) -> Unit
): Boolean {
    var hasNewProperties = false
    @Suppress("UNCHECKED_CAST")
    val newIterator = (this.properties as AbstractPropertyDefinitions<Any>).iterator()
    @Suppress("UNCHECKED_CAST")
    val storedIterator = (storedDataModel.properties as AbstractPropertyDefinitions<Any>).iterator()

    var newProperty = newIterator.next() as AnyDefinitionWrapper?
    var storedProperty = storedIterator.next() as AnyDefinitionWrapper?

    /**
     * Process new property not present on stored model. Trigger a migration if the new property
     * is required. Then there should be done something with the old objects.
     */
    fun processNew(newProp: AnyDefinitionWrapper) {
        hasNewProperties = true

        if (newProp.required) {
            handleMigrationReason("Required property ${newProp.index}:${newProp.name}")
        }

        newProperty = if (newIterator.hasNext()) newIterator.next() else null
    }

    /**
     * Stored value was not present in new data model so it should have been added to reserved
     * indices and names. Otherwise should be handled by a migration.
     */
    fun processStored(storedProp: AnyDefinitionWrapper) {
        if (this is IsRootDataModel<P>) {
            if (this.reservedIndices?.contains(storedProp.index) != true) {
                handleMigrationReason("Property with index ${storedProp.index} is not present in new model. Please add it to `reservedIndices` or add back the property to avoid this exception.")
            }
            val allNames = storedProp.alternativeNames?.let { it + storedProp.name } ?: setOf(storedProp.name)
            if (this.reservedNames?.containsAll(allNames) != true) {
                handleMigrationReason("Property with name(s) `${allNames.joinToString()}` is not present in new model. Please add it to `reservedNames` or add back the property to avoid this exception.")
            }
        }
        storedProperty = if (storedIterator.hasNext()) storedIterator.next() else null
    }

    /**
     * Compare stored with new properties. If incompatible changes are encountered a migration should be done
     */
    fun compareNewWithStored(storedProp: AnyDefinitionWrapper, newProp: AnyDefinitionWrapper) {
        newProp.compatibleWith(storedProp, handleMigrationReason)

        storedProperty = if (storedIterator.hasNext()) storedIterator.next() else null
        newProperty = if (newIterator.hasNext()) newIterator.next() else null
    }

    // Walk all stored and new properties of both data models and process them
    // depending on if they are present only in stored, new or both.
    while (newProperty != null || storedProperty != null) {
        val newProp = newProperty
        val storedProp = storedProperty
        when {
            newProp == null ->
                processStored(storedProp!!)
            storedProp == null ->
                processNew(newProp)
            newProp.index == storedProp.index ->
                compareNewWithStored(storedProp, newProp)
            newProp.index < storedProp.index ->
                if (newIterator.hasNext()) processNew(newProp) else processStored(storedProp)
            newProp.index > storedProp.index ->
                if (storedIterator.hasNext()) processStored(storedProp) else processNew(newProp)
        }
    }
    return hasNewProperties
}

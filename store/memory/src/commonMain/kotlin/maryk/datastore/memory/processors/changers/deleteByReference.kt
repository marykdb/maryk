@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors.changers

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.datastore.memory.records.DataRecordNode
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchPart

/**
 * Delete value by [reference] in [values] and record deletion below [version]
 * Add [handlePreviousValue] handler to pass previous value for extra operations
 * Return true if value was deleted. False if there was nothing to delete
 */
internal fun <T: Any> deleteByReference(
    values: MutableList<DataRecordNode>,
    reference: IsPropertyReference<T, IsPropertyDefinition<T>, *>,
    version: ULong,
    keepAllVersions: Boolean,
    handlePreviousValue: ((ByteArray, T?) -> Unit)? = null
): Boolean {
    val referenceToCompareTo = reference.toStorageByteArray()
    val valueIndex = values.binarySearch {
        it.reference.compareTo(referenceToCompareTo)
    }

    var shouldHandlePrevValue = true

    // Get previous value and convert if of complex type
    @Suppress("UNCHECKED_CAST")
    val prevValue: T = getValueAtIndex<T>(values, valueIndex)?.value.let {
        if(it == null){
            // does not exist so nothing to delete
            return false
        } else {
            // With delete the prev value for complex types needs to be set to check final and required states
            // Only current values are checked on content
            when (reference) {
                is MapReference<*, *, *> -> mapOf<Any, Any>() as T
                is ListReference<*, *> -> listOf<Any>() as T
                is SetReference<*, *> -> setOf<Any>() as T
                is MapValueReference<*, *, *> -> {
                    val mapReference = reference.parentReference as MapReference<Any, Any, IsPropertyContext>
                    val mapDefinition = mapReference.propertyDefinition.definition
                    createCountUpdater(values, mapReference as IsPropertyReference<Map<*, *>, IsPropertyDefinition<Map<*, *>>, out Any>, version, -1, keepAllVersions) { newCount ->
                        mapDefinition.validateSize(newCount) { mapReference }
                    }
                    // Map values can be set to null to be deleted.
                    shouldHandlePrevValue = false
                    it
                }
                is SetItemReference<*, *> -> {
                    val setReference = reference.parentReference as SetReference<Any, IsPropertyContext>
                    val setDefinition = setReference.propertyDefinition.definition
                    createCountUpdater(values, setReference as IsPropertyReference<Set<*>, IsPropertyDefinition<Set<*>>, out Any>, version, -1, keepAllVersions) { newCount ->
                        setDefinition.validateSize(newCount) { setReference }
                    }
                    // Map values can be set to null to be deleted.
                    shouldHandlePrevValue = false
                    it
                }
                else -> it
            }
        }
    }

    if (shouldHandlePrevValue) {
        // Primarily for validations
        handlePreviousValue?.invoke(referenceToCompareTo, prevValue)
    }

    // Delete complex sub parts below same reference
    for (index in valueIndex until values.size) {
        val value = values[index]

        if(value.reference.matchPart(0, referenceToCompareTo)) {
            deleteByIndex<T>(values, index, value.reference, version)
        } else {
            break
        }
    }

    return deleteByIndex<T>(values, valueIndex, referenceToCompareTo, version) != null
}

package maryk.datastore.memory.processors.changers

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.emptyValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsMapDefinitionWrapper
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.references.EmbeddedValuesPropertyRef
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.datastore.memory.records.DataRecordNode
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart

/**
 * Delete value by [reference] in [values] and record deletion below [version]
 * Add [handlePreviousValue] handler to pass previous value for extra operations
 * Return true if value was deleted. False if there was nothing to delete
 */
internal fun <T : Any> deleteByReference(
    values: MutableList<DataRecordNode>,
    reference: TypedPropertyReference<out T>,
    version: HLC,
    keepAllVersions: Boolean,
    handlePreviousValue: ((ByteArray, T?) -> Unit)? = null
): Boolean {
    if (reference is TypedValueReference<*, *, *>) {
        throw RequestException("Type Reference not allowed for deletes. Use the multi type parent.")
    }

    if (reference is ListItemReference<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val listReference = reference.parentReference as ListReference<Any, IsPropertyContext>
        val original = getList(values, listReference) ?: return false
        if (reference.index >= original.size.toUInt()) return false

        val newList = original.toMutableList().apply { removeAt(reference.index.toInt()) }
        listReference.propertyDefinition.validateWithRef(original, newList) { listReference }
        return setListValue(values, listReference, newList, original.size, version, keepAllVersions)
    }

    val referenceToCompareTo = reference.toStorageByteArray()
    val valueIndex = values.binarySearch {
        it.reference compareTo referenceToCompareTo
    }

    var shouldHandlePrevValue = true

    // Get previous value and convert if of complex type
    @Suppress("UNCHECKED_CAST")
    val prevValue: T = getValueAtIndex<T>(values, valueIndex)?.value.let {
        if (it == null) {
            // does not exist so nothing to delete
            // So skip out early
            return false
        } else {
            // With delete the prev value for complex types needs to be set to check final and required states
            // Only current values are checked on content
            when (reference) {
                is IsMapReference<*, *, *, *> -> mapOf<Any, Any>() as T
                is ListReference<*, *> -> listOf<Any>() as T
                is SetReference<*, *> -> setOf<Any>() as T
                is EmbeddedValuesPropertyRef<*, *> -> reference.propertyDefinition.definition.dataModel.emptyValues() as T
                is MapValueReference<*, *, *> -> {
                    val mapReference = reference.parentReference as IsMapReference<Any, Any, IsPropertyContext, IsMapDefinitionWrapper<Any, Any, Any, IsPropertyContext, *>>
                    createCountUpdater(
                        values,
                        mapReference as IsPropertyReference<Map<*, *>, IsPropertyDefinition<Map<*, *>>, out Any>,
                        version,
                        -1,
                        keepAllVersions
                    ) { newCount ->
                        mapReference.propertyDefinition.definition.validateSize(newCount) { mapReference }
                    }
                    // Map values can be set to null to be deleted.
                    shouldHandlePrevValue = false
                    it
                }
                is SetItemReference<*, *> -> {
                    val setReference = reference.parentReference as SetReference<Any, IsPropertyContext>
                    createCountUpdater(
                        values,
                        setReference as IsPropertyReference<Set<*>, IsPropertyDefinition<Set<*>>, out Any>,
                        version,
                        -1,
                        keepAllVersions
                    ) { newCount ->
                        setReference.propertyDefinition.definition.validateSize(newCount) { setReference }
                    }
                    // Set values can be set to null to be deleted.
                    shouldHandlePrevValue = false
                    it
                }
                is MultiTypePropertyReference<*, *, *, *, *> -> {
                    if (it is TypedValue<*, *>) {
                        it
                    } else if (it is MultiTypeEnum<*>) {
                        it.invoke(Unit) as T
                    } else throw StorageException("Unknown type $it for MultiTypePropertyReference")
                }
                else -> it
            }
        }
    }

    if (shouldHandlePrevValue) {
        // Primarily for validations
        handlePreviousValue?.invoke(referenceToCompareTo, prevValue)
    }

    var isDeleted = false

    // Delete value and complex sub parts below same reference
    for (index in valueIndex until values.size) {
        val value = values[index]
        if (value.reference.matchesRangePart(0, referenceToCompareTo)) {
            isDeleted = deleteByIndex<T>(values, index, value.reference, version, keepAllVersions) != null
        } else {
            break
        }
    }

    return isDeleted
}

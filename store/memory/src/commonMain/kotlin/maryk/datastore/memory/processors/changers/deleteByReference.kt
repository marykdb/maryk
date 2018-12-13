@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors.changers

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.datastore.memory.records.DataRecordNode
import maryk.lib.extensions.compare.compareTo

/**
 * Delete value by [reference] in [values] and record deletion below [version]
 * Add [handlePreviousValue] handler to pass previous value for extra operations
 * Return true if value was deleted. False if there was nothing to delete
 */
internal fun <T: Any> deleteByReference(
    values: MutableList<DataRecordNode>,
    reference: IsPropertyReference<T, IsPropertyDefinition<T>, *>,
    version: ULong,
    handlePreviousValue: ((ByteArray, T?) -> Unit)? = null
): Boolean {
    val referenceToCompareTo = reference.toStorageByteArray()
    val valueIndex = values.binarySearch {
        it.reference.compareTo(referenceToCompareTo)
    }

    handlePreviousValue?.invoke(referenceToCompareTo, getValueAtIndex<T>(values, valueIndex)?.value)

    return deleteByIndex<T>(values, valueIndex, referenceToCompareTo, version) != null
}

package maryk.datastore.memory.processors.changers

import maryk.core.properties.references.IsPropertyReference
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.lib.extensions.compare.compareTo

/**
 * Set [value] at [reference] below [version] in [values]
 * Use [keepAllVersions] on true to keep all previous values
 * Add [validate] handler to pass previous value for validation
 * Return true if changed
 */
internal fun <T : Any> setValue(
    values: MutableList<DataRecordNode>,
    reference: IsPropertyReference<T, *, *>,
    value: T,
    version: ULong,
    keepAllVersions: Boolean = false,
    validate: ((DataRecordValue<T>, T?) -> Unit)? = null
): Boolean {
    val referenceToCompareTo = reference.toStorageByteArray()

    val valueIndex = values.binarySearch {
        it.reference.compareTo(referenceToCompareTo)
    }

    val newDataValue = setValueAtIndex(
        values, valueIndex, referenceToCompareTo, value, version, keepAllVersions
    )

    // Validate value if it changed
    newDataValue?.let {
        validate?.invoke(newDataValue, getValueAtIndex<T>(values, valueIndex)?.value)
    }

    return newDataValue != null
}

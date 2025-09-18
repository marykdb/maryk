package maryk.datastore.indexeddb.processors.changers

import maryk.core.clock.HLC
import maryk.core.properties.references.TypedPropertyReference
import maryk.datastore.indexeddb.records.DataRecordNode
import maryk.datastore.indexeddb.records.DataRecordValue
import maryk.lib.extensions.compare.compareTo

/**
 * Set [value] at [reference] below [version] in [values]
 * Use [keepAllVersions] on true to keep all previous values
 * Add [validate] handler to pass previous value for validation
 * Return true if changed
 */
internal fun <T : Any> setValue(
    values: MutableList<DataRecordNode>,
    reference: TypedPropertyReference<out T>,
    value: T,
    version: HLC,
    keepAllVersions: Boolean = false,
    validate: ((DataRecordValue<T>, T?) -> Unit)? = null
): Boolean = setValue(
    values,
    reference.toStorageByteArray(),
    value,
    version,
    keepAllVersions,
    validate
)

/**
 * Set [value] at [reference] below [version] in [values]
 * Use [keepAllVersions] on true to keep all previous values
 * Add [validate] handler to pass previous value for validation
 * Return true if changed
 */
internal fun <T : Any> setValue(
    values: MutableList<DataRecordNode>,
    reference: ByteArray,
    value: T,
    version: HLC,
    keepAllVersions: Boolean = false,
    validate: ((DataRecordValue<T>, T?) -> Unit)? = null
): Boolean {
    val valueIndex = values.binarySearch {
        it.reference compareTo reference
    }

    val prevValue = getValueAtIndex<T>(values, valueIndex)?.value

    val newDataValue = setValueAtIndex(
        values, valueIndex, reference, value, version, keepAllVersions
    )

    // Validate value if it changed
    newDataValue?.let {
        validate?.invoke(newDataValue, prevValue)
    }

    return newDataValue != null
}

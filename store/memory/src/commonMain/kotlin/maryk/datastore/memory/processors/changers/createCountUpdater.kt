package maryk.datastore.memory.processors.changers

import maryk.core.clock.HLC
import maryk.core.properties.references.TypedPropertyReference
import maryk.datastore.memory.records.DataRecordNode
import maryk.lib.extensions.compare.compareTo

/**
 * Set count in [values] for [reference] at [version] by applying [countChange] to current count
 * Use [keepAllVersions] on true to keep all versions
 */
internal fun <T : Any> createCountUpdater(
    values: MutableList<DataRecordNode>,
    reference: TypedPropertyReference<out T>,
    version: HLC,
    countChange: Int,
    keepAllVersions: Boolean,
    sizeValidator: (UInt) -> Unit
) {
    val referenceToCompareTo = reference.toStorageByteArray()

    val valueIndex = values.binarySearch {
        it.reference compareTo referenceToCompareTo
    }

    val previousCount = getValueAtIndex<Int>(values, valueIndex)?.value ?: 0
    val newCount = previousCount + countChange

    sizeValidator(newCount.toUInt())

    setValueAtIndex(values, valueIndex, referenceToCompareTo, newCount, version, keepAllVersions)
}

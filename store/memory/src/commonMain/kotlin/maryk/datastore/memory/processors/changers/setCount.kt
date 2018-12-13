@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors.changers

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.datastore.memory.records.DataRecordNode
import maryk.lib.extensions.compare.compareTo

/**
 * Set count in [values] for [reference] at [version] by applying [countChange] to current count
 * Use [keepAllVersions] on true to keep all versions
 */
internal fun <T: Any> createCountUpdater(
    values: MutableList<DataRecordNode>,
    reference: IsPropertyReference<out T, IsPropertyDefinition<out T>, out Any>,
    version: ULong,
    countChange: Int,
    keepAllVersions: Boolean
): Int {
    val referenceToCompareTo = reference.toStorageByteArray()

    val valueIndex = values.binarySearch {
        it.reference.compareTo(referenceToCompareTo)
    }

    val previousCount = getValueAtIndex<Int>(values, valueIndex)?.value ?: 0
    val newCount = previousCount + countChange

    setValueAtIndex(values, valueIndex, referenceToCompareTo, newCount, version, keepAllVersions)

    return newCount
}

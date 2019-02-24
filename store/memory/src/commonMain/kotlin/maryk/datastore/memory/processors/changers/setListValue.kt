package maryk.datastore.memory.processors.changers

import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListReference
import maryk.datastore.memory.records.DataRecordNode
import maryk.lib.extensions.compare.compareTo

/**
 * Set a list value in [values] for [reference] with a [newList] at new [version].
 * With [originalCount] it is determined if items need to be deleted.
 * Use [keepAllVersions] on true to keep old versions
 * Returns true if changed
 */
fun <T : Any> setListValue(
    values: MutableList<DataRecordNode>,
    reference: IsPropertyReference<out List<T>, IsPropertyDefinition<out List<T>>, out Any>,
    newList: List<T>,
    originalCount: Int,
    version: ULong,
    keepAllVersions: Boolean
): Boolean {
    @Suppress("UNCHECKED_CAST")
    val listReference = reference as ListReference<T, *>
    val referenceToCompareTo = listReference.toStorageByteArray()

    var valueIndex = values.binarySearch {
        it.reference.compareTo(referenceToCompareTo)
    }

    // Set the count
    setValueAtIndex(values, valueIndex, referenceToCompareTo, newList.size, version, keepAllVersions)

    val toDeleteCount = originalCount - newList.size
    if (toDeleteCount > 0) {
        for (i in 0..toDeleteCount) {
            deleteByIndex<T>(values, valueIndex + i, getValueAtIndex<T>(values, valueIndex + i)!!.reference, version)
        }
    }

    // Where is last addition
    val lastAdditionIndex = if (valueIndex > 0 && toDeleteCount < 0) {
        valueIndex + originalCount
    } else 0

    var changed = false

    // Walk all new values to store
    newList.forEachIndexed { index, item ->
        var byteIndex = referenceToCompareTo.size
        val newRef = referenceToCompareTo.copyOf(byteIndex + 4)

        index.toUInt().writeBytes({
            newRef[byteIndex++] = it
        })

        if (valueIndex < 0) {
            valueIndex--
        } else {
            if (lastAdditionIndex <= valueIndex) {
                valueIndex = valueIndex * -1 - 2
            } else valueIndex++
        }

        setValueAtIndex(values, valueIndex, newRef, item, version, keepAllVersions)?.also {
            changed = true
        }
    }

    return changed
}

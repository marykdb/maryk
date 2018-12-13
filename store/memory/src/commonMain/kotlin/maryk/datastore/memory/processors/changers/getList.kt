package maryk.datastore.memory.processors.changers

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.datastore.memory.records.DataRecordNode
import maryk.lib.extensions.compare.compareTo

/**
 * Get list from [values] at [reference] by reading and collecting all values from DataRecord
 */
internal fun <T: Any> getList(
    values: List<DataRecordNode>,
    reference: IsPropertyReference<out List<T>, IsPropertyDefinition<out List<T>>, out Any>
): MutableList<T>? {
    val referenceToCompareTo = reference.toStorageByteArray()

    var valueIndex = values.binarySearch {
        it.reference.compareTo(referenceToCompareTo)
    }

    if (valueIndex < 0) return null

    val count = getValueAtIndex<Int>(values, valueIndex)?.value ?: 0
    valueIndex++ // skip count index

    val list = ArrayList<T>(count)
    while (valueIndex < values.size) {
        val node = getValueAtIndex<T>(values, valueIndex) ?: continue

        // Break if reference does not match list start
        if (!node.reference.matchStart(referenceToCompareTo)) {
            break
        }

        list.add(node.value)
        valueIndex++
    }
    return list
}

/** Match given [startBytes] against starting bytes of this byte array. Return true if match */
private fun ByteArray.matchStart(startBytes: ByteArray): Boolean {
    if (startBytes.size > this.size) return false

    startBytes.forEachIndexed { index, byte ->
        if (this[index] != byte) return false
    }
    return true
}

package maryk.datastore.memory.processors.changers

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IncMapReference
import maryk.datastore.memory.records.DataRecordNode
import maryk.lib.extensions.compare.compareDefinedTo
import maryk.lib.extensions.compare.compareTo

/** Get the current incrementing map key for [reference] from [values] */
internal fun getCurrentIncMapKey(
    values: List<DataRecordNode>,
    reference: IncMapReference<Comparable<Any>, Any, IsPropertyContext>
): ByteArray {
    val referenceToCompareTo = reference.toStorageByteArray()

    val valueIndex = values.binarySearch {
        it.reference compareTo referenceToCompareTo
    }

    val nextValueReference = values.getOrNull(valueIndex + 1)?.reference

    return if (nextValueReference != null && referenceToCompareTo.compareDefinedTo(nextValueReference, 0) == 0) {
        nextValueReference
    } else {
        // If nothing was found create a new reference
        val mapKeySize = reference.propertyDefinition.definition.keyDefinition.byteSize
        return ByteArray(mapKeySize + 1 + referenceToCompareTo.size) { if(it < referenceToCompareTo.size) referenceToCompareTo[it] else if (it == referenceToCompareTo.size) mapKeySize.toByte() else 0xFF.toByte() }
    }
}

package maryk.datastore.memory.processors.changers

import maryk.datastore.shared.incrementingMapAllocationPrefix
import maryk.datastore.shared.decodeIncrementingMapMaximum
import maryk.datastore.shared.StateBackupAllocations
import maryk.core.properties.types.Bytes

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IncMapReference
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.shared.incrementingMapKeyPrefix
import maryk.lib.extensions.compare.compareDefinedRange
import maryk.lib.extensions.compare.compareTo

/** Get the current incrementing map key for [reference] from [values] */
internal fun getCurrentIncMapKey(
    values: List<DataRecordNode>,
    reference: IncMapReference<Comparable<Any>, Any, IsPropertyContext>,
    allocations: StateBackupAllocations = emptyMap(),
): ByteArray {
    val referenceToCompareTo = reference.toStorageByteArray()

    val valueIndex = values.binarySearch {
        it.reference compareTo referenceToCompareTo
    }

    val nextValueReference = values.getOrNull(valueIndex + 1)?.reference

    val current = if (nextValueReference != null && referenceToCompareTo.compareDefinedRange(nextValueReference, 0) == 0) {
        incrementingMapKeyPrefix(nextValueReference, referenceToCompareTo, reference.propertyDefinition.definition.keyDefinition.byteSize)
    } else {
        // If nothing was found create a new reference
        null
    }
    return incrementingMapAllocationPrefix(reference, current, allocations[Bytes(referenceToCompareTo)]?.let { decodeIncrementingMapMaximum(reference, it.bytes) })
}

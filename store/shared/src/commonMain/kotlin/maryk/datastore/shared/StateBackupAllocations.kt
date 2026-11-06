package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.Bytes

/** Storage-encoded property references and lifetime maxima, independent of backend/model IDs. */
typealias StateBackupAllocations = Map<Bytes, Bytes>

/** Bounded collector shared by every backend, including maxima for currently absent parents. */
class StateBackupAllocationCollector(private val model: IsRootDataModel) {
    private val maxima = mutableMapOf<Bytes, Bytes>()
    private var bytes = 4

    fun add(referenceBytes: ByteArray, maximumBytes: ByteArray) {
        var offset = 0
        @Suppress("UNCHECKED_CAST")
        val reference = model.getPropertyReferenceByStorageBytes(referenceBytes.size, { referenceBytes[offset++] })
            as? IncMapReference<out Comparable<Any>, out Any, *>
            ?: throw RequestException("State allocation reference must identify an incrementing map")
        if (offset != referenceBytes.size || !reference.toStorageByteArray().contentEquals(referenceBytes) ||
            maximumBytes.size != reference.propertyDefinition.definition.keyDefinition.byteSize
        ) throw RequestException("Invalid state allocation encoding")
        val maximum = decodeIncrementingMapMaximum(reference, maximumBytes)
        if (!encodeIncrementingMapMaximum(IncrementingMapMaximum(reference, maximum)).contentEquals(maximumBytes)) {
            throw RequestException("Invalid state allocation maximum")
        }
        val key = Bytes(referenceBytes)
        val previous = maxima[key]
        if (previous == null) {
            bytes += 8 + referenceBytes.size + maximumBytes.size
            if (bytes > MAX_ATOMIC_BYTES) throw RequestException("State allocation metadata exceeds byte limit")
        }
        if (previous == null || maximum > decodeIncrementingMapMaximum(reference, previous.bytes)) maxima[key] = Bytes(maximumBytes)
    }

    fun snapshot(): StateBackupAllocations = maxima.toMap()
}

internal fun stateAllocationBytes(allocations: StateBackupAllocations): Int =
    allocations.entries.fold(4L) { size, (reference, maximum) -> size + 8 + reference.size + maximum.size }
        .also { if (it > MAX_ATOMIC_BYTES) throw RequestException("State allocation metadata exceeds byte limit") }.toInt()

internal fun snapshotStateAllocations(model: IsRootDataModel, allocations: StateBackupAllocations): StateBackupAllocations =
    StateBackupAllocationCollector(model).apply {
        allocations.forEach { (reference, maximum) -> add(reference.bytes, maximum.bytes) }
    }.snapshot()

/** Check the aggregate budget before detaching any externally supplied metadata maps. */
internal fun snapshotStateAllocationRecords(
    model: IsRootDataModel,
    keys: Set<Key<IsRootDataModel>>,
    allocations: Map<Key<IsRootDataModel>, StateBackupAllocations>,
): Map<Key<IsRootDataModel>, StateBackupAllocations> {
    var bytes = 0L
    for ((key, maxima) in allocations) {
        if (key !in keys) throw RequestException("State allocations refer to a missing record")
        bytes += stateAllocationBytes(maxima)
        if (bytes > MAX_ATOMIC_BYTES) throw RequestException("State allocation metadata exceeds chunk byte limit")
    }
    return allocations.mapValues { snapshotStateAllocations(model, it.value) }
}

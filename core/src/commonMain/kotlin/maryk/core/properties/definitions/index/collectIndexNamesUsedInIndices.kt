package maryk.core.properties.definitions.index

import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference
import maryk.lib.extensions.compare.compareTo

/** Collect references used in [indices] in sorted list referring to IsIndexable describing it */
internal fun collectReferencesUsedInIndices(indices: List<IsIndexable>?): List<ReferenceToIndexable>? {
    if (indices == null) return null

    val result = mutableListOf<ReferenceToIndexable>()

    for (index in indices) {
        findReferences(index) { reference ->
            val i = result.binarySearch { it.reference.compareTo(reference) }
            if (i < 0) {
                result.add(
                    i * -1 - 1,
                    ReferenceToIndexable(reference, arrayOf(index))
                )
            } else {
                result[i] = ReferenceToIndexable(reference, result[i].indexables.plus(index))
            }
        }
    }

    return result
}

private fun findReferences(index: IsIndexable, add: (ByteArray) -> Unit) {
    when (index) {
        is UUIDKey -> {}
        is TypeId<*> -> add(index.reference.toStorageByteArray())
        is Reversed<*> -> add(index.reference.toStorageByteArray())
        is ValueWithFixedBytesPropertyReference<*, *, *, *> -> add(index.toStorageByteArray())
        is ValueWithFlexBytesPropertyReference<*, *, *, *> -> add(index.toStorageByteArray())
        is Multiple -> {
            index.references.forEach {
                findReferences(it, add)
            }
        }
        else -> throw Exception("Unknown type for index $index")
    }
}

package maryk.datastore.shared

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.references.AnyPropertyReference

/** A replacement affects both descendant claims and a unique value enclosing the changed field. */
fun ByteArray.isAffectedUniqueClaim(references: List<ByteArray>?): Boolean =
    references == null || references.any { other ->
        (0 until minOf(size, other.size)).all { this[it] == other[it] }
    }

/** Minimal qualifier ranges needed to reconcile a mutation's current unique claims. */
fun IsRootDataModel.uniqueClaimScopes(references: List<ByteArray>?): List<ByteArray> {
    if (references == null) return listOf(byteArrayOf())
    val scopes = references.toMutableList()
    for (reference in references) {
        var offset = 0
        val property = getPropertyReferenceByStorageBytes(reference.size, { reference[offset++] })
        for (ancestor: AnyPropertyReference in listOf(property) + storageReferenceParents(property)) {
            if ((ancestor.comparablePropertyDefinition as? IsComparableDefinition<*, *>)?.unique == true) {
                scopes += ancestor.toStorageByteArray()
            }
        }
    }
    return scopes.sortedBy { it.size }.fold(mutableListOf()) { selected, scope ->
        if (selected.none { existing -> existing.indices.all { scope[it] == existing[it] } }) selected += scope
        selected
    }
}

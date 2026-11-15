package maryk.datastore.shared

import maryk.core.properties.types.Bytes

/** Backend-owned candidate encodings, including tokens retained during encryption-key rotation. */
data class CurrentUniqueClaim(val reference: ByteArray, val candidates: List<ByteArray>)

data class ObsoleteUniqueClaim(val reference: ByteArray, val value: ByteArray, val storageKey: ByteArray)

/** Plan only: the backend must still check ownership and remove claims inside its transaction. */
fun obsoleteUniqueClaims(before: List<CurrentUniqueClaim>, after: List<CurrentUniqueClaim>): List<ObsoleteUniqueClaim> {
    if (before.isEmpty()) return emptyList()
    val retained = after.flatMap { claim -> claim.candidates.map { claim.reference + it } }
    // Tiny scalar claims avoid a hash table; larger collections avoid quadratic membership work.
    val indexed = if (retained.size > 8) retained.mapTo(HashSet(retained.size)) { Bytes(it) } else null
    return buildList {
        for (claim in before) for (value in claim.candidates) {
            val storageKey = claim.reference + value
            val keep = indexed?.contains(Bytes(storageKey)) ?: retained.any { it.contentEquals(storageKey) }
            if (!keep) add(ObsoleteUniqueClaim(claim.reference, value, storageKey))
        }
    }
}

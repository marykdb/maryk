package maryk.core.query.filters

import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.query.DefinedByReference
import maryk.core.query.pairs.ReferenceValuePair

/** Filter to DefinedByReference pairs */
interface IsReferenceAnyPairsFilter<D: DefinedByReference<*>> : IsFilter {
    val referenceValuePairs: List<D>

    override fun singleReference(predicate: (IsPropertyReference<*, *, *>) -> Boolean): IsPropertyReference<*, *, *>? {
        for (pair in this.referenceValuePairs) {
            var parentReference: AnyPropertyReference? = pair.reference
            do {
                if (predicate(parentReference!!)) {
                    return pair.reference
                }
                parentReference = (parentReference as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference
            } while (parentReference != null)
        }
        return null
    }
}

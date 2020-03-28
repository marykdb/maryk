package maryk.core.query.filters

import maryk.core.properties.references.IsPropertyReference

/** Filter that contains a list of filters */
interface IsFilterList: IsFilter {
    val filters: List<IsFilter>

    override fun singleReference(predicate: (IsPropertyReference<*, *, *>) -> Boolean): IsPropertyReference<*, *, *>? {
        for (andFilter in this.filters) {
            andFilter.singleReference(predicate)?.let {
                return it
            }
        }

        return null
    }
}

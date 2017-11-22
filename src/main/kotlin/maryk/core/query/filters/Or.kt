package maryk.core.query.filters

/** Does an OR comparison against given filters. If one returns true the entire result will be true.
 * @param filters to check against with or
 * @param T: type of value to be operated on
 */
data class Or<T: Any>(
        val filters: List<IsFilter>
) : IsFilter 
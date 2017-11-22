package maryk.core.query.filters

/** Does an AND comparison against given filters. Only if all given filters return true will the entire result be true.
 * @param filters to check against with and
 * @param T: type of value to be operated on
 */
data class And<T: Any>(
        val filters: List<IsFilter>
) : IsFilter 
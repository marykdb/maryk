package maryk.core.query.filters

/** Reverses the boolean check for given filter
 * @param filter to check against
 * @param T: type of value to be operated on
 */
data class Not<T: Any>(
        val filter: IsFilter
) : IsFilter
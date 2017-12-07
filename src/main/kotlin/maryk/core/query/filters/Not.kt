package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue

/** Reverses the boolean check for given filter
 * @param filter to check against
 */
data class Not(
        val filter: IsFilter
) : IsFilter {
    override val filterType = FilterType.NOT

    companion object: QueryDataModel<Not>(
            properties = object : PropertyDefinitions<Not>() {
                init {
                    add(0, "filter", MultiTypeDefinition(
                            getDefinition = { mapOfFilterDefinitions[it] }
                    )) { not: Not -> TypedValue(not.filter.filterType.index, not.filter)}
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Not(
                filter = (map[0] as TypedValue<IsFilter>).value
        )
    }
}
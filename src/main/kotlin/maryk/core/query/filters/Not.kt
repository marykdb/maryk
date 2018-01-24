package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue

/** Reverses the boolean check for given [filter] */
data class Not(
    val filter: IsFilter
) : IsFilter {
    override val filterType = FilterType.NOT

    internal companion object: QueryDataModel<Not>(
        properties = object : PropertyDefinitions<Not>() {
            init {
                add(0, "filter", MultiTypeDefinition(
                    definitionMap = mapOfFilterDefinitions
                )) { not: Not -> TypedValue(not.filter.filterType, not.filter)}
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Not(
            filter = (map[0] as TypedValue<FilterType, IsFilter>).value
        )
    }
}
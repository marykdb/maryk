package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue

/** Reverses the boolean check for given [filter] */
data class Not(
    val filter: IsFilter
) : IsFilter {
    override val filterType = FilterType.Not

    internal companion object: QueryDataModel<Not>(
        properties = object : PropertyDefinitions<Not>() {
            init {
                add(0, "filter",
                    MultiTypeDefinition(
                        typeEnum = FilterType,
                        definitionMap = mapOfFilterDefinitions
                    ),
                    getter = { TypedValue(it.filter.filterType, it.filter) }
                )
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = Not(
            filter = map<TypedValue<FilterType, IsFilter>>(0).value
        )
    }
}

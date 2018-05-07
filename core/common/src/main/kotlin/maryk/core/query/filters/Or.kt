package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue

/** Does an Or comparison against given [filters]. If one returns true the entire result will be true. */
data class Or(
    val filters: List<IsFilter>
) : IsFilter {
    override val filterType = FilterType.Or

    constructor(vararg filters: IsFilter) : this(filters.toList())

    internal companion object: QueryDataModel<Or>(
        properties = object : PropertyDefinitions<Or>() {
            init {
                add(0, "filters",
                    ListDefinition(
                        valueDefinition = MultiTypeDefinition(
                            definitionMap = mapOfFilterDefinitions
                        )
                    ),
                    getter = Or::filters,
                    toSerializable = { TypedValue(it.filterType, it) },
                    fromSerializable = { it.value as IsFilter }
                )
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = Or(
            filters = map<List<IsFilter>>(0)
        )
    }
}

package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue

/** Does an And comparison against given [filters]. Only if all given filters return true will the entire result be true. */
data class And(
    val filters: List<IsFilter>
) : IsFilter {
    override val filterType = FilterType.And

    constructor(vararg filters: IsFilter) : this(filters.toList())

    internal companion object: QueryDataModel<And>(
        properties = object : PropertyDefinitions<And>() {
            init {
                add(0, "filters", ListDefinition(
                    valueDefinition = MultiTypeDefinition(
                        definitionMap = mapOfFilterDefinitions
                    )
                )) { it.filters.map { TypedValue(it.filterType, it) } }
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = And(
            filters = map<List<TypedValue<FilterType, IsFilter>>>(0).map { it.value }
        )
    }
}

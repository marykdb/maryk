package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.types.TypedValue

/** Does an OR comparison against given filters. If one returns true the entire result will be true.
 * @param filters to check against with or
 */
data class Or(
        val filters: List<IsFilter>
) : IsFilter {
    override val filterType = FilterType.OR

    constructor(vararg filters: IsFilter) : this(filters.toList())

    object Properties {
        val filters = ListDefinition(
                name = "filters",
                index = 0,
                required = true,
                valueDefinition = MultiTypeDefinition(
                        required = true,
                        getDefinition = { mapOfFilterDefinitions[it] }
                )
        )
    }

    companion object: QueryDataModel<Or>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                Or(
                        filters = (it[0] as List<TypedValue<IsFilter>>).map { it.value }
                )
            },
            definitions = listOf(
                    Def(Properties.filters, { it.filters.map { TypedValue(it.filterType.index, it) } })
            )
    )
}
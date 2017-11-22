package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.types.TypedValue

/** Does an AND comparison against given filters. Only if all given filters return true will the entire result be true.
 * @param filters to check against with and
 */
data class And(
        val filters: List<IsFilter>
) : IsFilter {
    override val filterType = FilterType.AND

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

    companion object: QueryDataModel<And>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                And(
                        filters = (it[0] as List<TypedValue<IsFilter>>).map { it.value }
                )
            },
            definitions = listOf(
                    Def(Properties.filters, { it.filters.map { TypedValue(it.filterType.index, it) } })
            )
    )
}
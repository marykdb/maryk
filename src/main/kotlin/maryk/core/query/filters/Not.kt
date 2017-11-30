package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.types.TypedValue

/** Reverses the boolean check for given filter
 * @param filter to check against
 */
data class Not(
        val filter: IsFilter
) : IsFilter {
    override val filterType = FilterType.NOT

    object Properties {
        val filter = MultiTypeDefinition(
                name = "filter",
                index = 0,
                required = true,
                getDefinition = { mapOfFilterDefinitions[it] }
        )
    }

    companion object: QueryDataModel<Not>(
            definitions = listOf(
                    Def(Properties.filter, { not: Not -> TypedValue(not.filter.filterType.index, not.filter)})
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Not(
                filter = (map[0] as TypedValue<IsFilter>).value
        )
    }
}
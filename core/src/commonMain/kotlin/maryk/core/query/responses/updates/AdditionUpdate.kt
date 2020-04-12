package maryk.core.query.responses.updates

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.AbstractValuesDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.contextual.embedContextual
import maryk.core.properties.definitions.number
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.query.responses.statuses.addKey
import maryk.core.query.responses.updates.UpdateResponseType.Addition
import maryk.core.values.SimpleObjectValues
import maryk.core.values.Values

/** Update response describing an addition to query result of [values] at [key] */
data class AdditionUpdate<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val key: Key<DM>,
    override val version: ULong,
    val insertionIndex: Int,
    val values: Values<DM, P>
) : IsUpdateResponse<DM, P> {
    override val type = Addition

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<AdditionUpdate<*, *>>() {
        val key by addKey(AdditionUpdate<*, *>::key)
        val version by number(2u, getter = AdditionUpdate<*, *>::version, type = UInt64)
        val insertionIndex by number(3u, getter = AdditionUpdate<*, *>::insertionIndex, type = SInt32)
        val values by embedContextual(
            index = 4u,
            getter = AdditionUpdate<*, *>::values,
            contextualResolver = { context: RequestContext? ->
                @Suppress("UNCHECKED_CAST")
                context?.dataModel as? AbstractValuesDataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, RequestContext>?
                    ?: throw ContextNotFoundException()
            }
        )
    }

    internal companion object : SimpleQueryDataModel<AdditionUpdate<*, *>>(
        properties = Properties
    ) {
        override fun invoke(values: SimpleObjectValues<AdditionUpdate<*, *>>) = AdditionUpdate<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
            key = values(1u),
            version = values(2u),
            insertionIndex = values(3u),
            values = values(4u)
        )
    }
}

package maryk.core.query.responses.updates

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.definitions.IsValuesDataModel
import maryk.core.properties.IsRootModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.TypedValuesModel
import maryk.core.properties.definitions.boolean
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
data class AdditionUpdate<DM: IsRootModel>(
    val key: Key<DM>,
    override val version: ULong,
    val firstVersion: ULong,
    val insertionIndex: Int,
    val isDeleted: Boolean,
    val values: Values<DM>
) : IsUpdateResponse<DM> {
    override val type = Addition

    companion object : SimpleQueryModel<AdditionUpdate<*>>() {
        val key by addKey(AdditionUpdate<*>::key)
        val version by number(2u, getter = AdditionUpdate<*>::version, type = UInt64)
        val firstVersion by number(3u, getter = AdditionUpdate<*>::firstVersion, type = UInt64)
        val insertionIndex by number(4u, getter = AdditionUpdate<*>::insertionIndex, type = SInt32)
        val isDeleted by boolean(index = 5u, getter = AdditionUpdate<*>::isDeleted)
        val values by embedContextual(
            index = 6u,
            getter = AdditionUpdate<*>::values,
            contextualResolver = { context: RequestContext? ->
                @Suppress("UNCHECKED_CAST")
                context?.dataModel as? TypedValuesModel<IsValuesDataModel<IsValuesPropertyDefinitions>, IsValuesPropertyDefinitions>
                    ?: throw ContextNotFoundException()
            }
        )

        override fun invoke(values: SimpleObjectValues<AdditionUpdate<*>>) = AdditionUpdate<IsRootModel>(
            key = values(1u),
            version = values(2u),
            firstVersion = values(3u),
            insertionIndex = values(4u),
            isDeleted = values(5u),
            values = values(6u)
        )
    }
}

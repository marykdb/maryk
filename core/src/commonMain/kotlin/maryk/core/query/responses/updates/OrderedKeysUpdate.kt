package maryk.core.query.responses.updates

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.query.responses.updates.UpdateResponseType.OrderedKeys
import maryk.core.values.SimpleObjectValues

/**
 * Update response for describing the initial order and visibility of the Values
 * when requesting changes from a Get or Scan.
 * This is sent for the initial change and describes the order of [keys] at [version]
 *
 * This way the listener is always sure of the current state on which orders are changed.
 */
data class OrderedKeysUpdate<DM: IsRootValuesDataModel<P>, P: IsValuesPropertyDefinitions>(
    val keys: List<Key<DM>>,
    override val version: ULong,
    val sortingKeys: List<Bytes>? = null
) : IsUpdateResponse<DM, P> {
    override val type = OrderedKeys

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<OrderedKeysUpdate<*, *>>() {
        val keys by list(
            index = 1u,
            getter = OrderedKeysUpdate<*, *>::keys,
            valueDefinition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )

        val version by number(2u, getter = OrderedKeysUpdate<*, *>::version, type = UInt64)

        val sortingKeys by list(
            index = 3u,
            getter = OrderedKeysUpdate<*, *>::sortingKeys,
            required = false,
            valueDefinition = FlexBytesDefinition()
        )
    }

    internal companion object : SimpleQueryDataModel<OrderedKeysUpdate<*, *>>(
        properties = Properties
    ) {
        override fun invoke(values: SimpleObjectValues<OrderedKeysUpdate<*, *>>) = OrderedKeysUpdate<IsRootValuesDataModel<IsValuesPropertyDefinitions>, IsValuesPropertyDefinitions>(
            keys = values(1u),
            version = values(2u),
            sortingKeys = values(3u)
        )
    }
}

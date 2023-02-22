@file:Suppress("unused")

package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.AbstractValuesDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.contextual.embedContextual
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64
import maryk.core.values.ObjectValues
import maryk.core.values.Values

data class ValuesWithMetaData<DM : IsRootDataModel<P>, P : IsValuesPropertyDefinitions>(
    val key: Key<DM>,
    val values: Values<DM, P>,
    val firstVersion: ULong,
    val lastVersion: ULong,
    val isDeleted: Boolean
) {
    object Properties : ObjectPropertyDefinitions<ValuesWithMetaData<*, *>>() {
        val key by contextual(
            index = 1u,
            getter = ValuesWithMetaData<*, *>::key,
            definition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )

        val values by embedContextual(
            index = 2u,
            getter = ValuesWithMetaData<*, *>::values,
            contextualResolver = { context: RequestContext? ->
                @Suppress("UNCHECKED_CAST")
                context?.dataModel as? AbstractValuesDataModel<IsValuesDataModel<IsValuesPropertyDefinitions>, IsValuesPropertyDefinitions, RequestContext>?
                    ?: throw ContextNotFoundException()
            }
        )
        val firstVersion by number(3u, ValuesWithMetaData<*, *>::firstVersion, UInt64)
        val lastVersion by number(4u, ValuesWithMetaData<*, *>::lastVersion, UInt64)
        val isDeleted by boolean(5u, ValuesWithMetaData<*, *>::isDeleted)
    }

    companion object : QueryDataModel<ValuesWithMetaData<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ValuesWithMetaData<*, *>, Properties>) =
            ValuesWithMetaData<IsRootDataModel<IsValuesPropertyDefinitions>, IsValuesPropertyDefinitions>(
                key = values(1u),
                values = values(2u),
                firstVersion = values(3u),
                lastVersion = values(4u),
                isDeleted = values(5u)
            )
    }
}

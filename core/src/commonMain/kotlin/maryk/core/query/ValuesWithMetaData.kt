package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.QueryModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.contextual.embedContextual
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64
import maryk.core.values.ObjectValues
import maryk.core.values.Values

data class ValuesWithMetaData<DM : IsRootDataModel>(
    val key: Key<DM>,
    val values: Values<DM>,
    val firstVersion: ULong,
    val lastVersion: ULong,
    val isDeleted: Boolean
) {
    companion object : QueryModel<ValuesWithMetaData<*>, Companion>() {
        val key by contextual(
            index = 1u,
            getter = ValuesWithMetaData<*>::key,
            definition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? IsRootDataModel ?: throw ContextNotFoundException()
                }
            )
        )

        val values by embedContextual(
            index = 2u,
            getter = ValuesWithMetaData<*>::values,
            contextualResolver = { context: RequestContext? ->
                @Suppress("UNCHECKED_CAST")
                context?.dataModel as? TypedValuesDataModel<IsValuesDataModel>
                    ?: throw ContextNotFoundException()
            }
        )
        val firstVersion by number(3u, ValuesWithMetaData<*>::firstVersion, UInt64)
        val lastVersion by number(4u, ValuesWithMetaData<*>::lastVersion, UInt64)
        val isDeleted by boolean(5u, ValuesWithMetaData<*>::isDeleted)

        override fun invoke(values: ObjectValues<ValuesWithMetaData<*>, Companion>) =
            ValuesWithMetaData<IsRootDataModel>(
                key = values(key.index),
                values = values(Companion.values.index),
                firstVersion = values(firstVersion.index),
                lastVersion = values(lastVersion.index),
                isDeleted = values(isDeleted.index)
            )
    }
}

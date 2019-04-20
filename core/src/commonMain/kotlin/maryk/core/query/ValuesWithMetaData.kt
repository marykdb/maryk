@file:Suppress("unused")

package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.AbstractValuesDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64
import maryk.core.values.ObjectValues
import maryk.core.values.Values

data class ValuesWithMetaData<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions>(
    val key: Key<DM>,
    val values: Values<DM, P>,
    val firstVersion: ULong,
    val lastVersion: ULong,
    val isDeleted: Boolean
) {
    object Properties : ObjectPropertyDefinitions<ValuesWithMetaData<*, *>>() {
        val key = add(1u, "key", ContextualReferenceDefinition<RequestContext>(
            contextualResolver = {
                it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
            }
        ), ValuesWithMetaData<*, *>::key)

        @Suppress("UNCHECKED_CAST")
        val values = add(2u, "values",
            ContextualEmbeddedValuesDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? AbstractValuesDataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, RequestContext>?
                        ?: throw ContextNotFoundException()
                }
            ) as IsEmbeddedValuesDefinition<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions, RequestContext>,
            ValuesWithMetaData<*, *>::values as (ValuesWithMetaData<*, *>) -> Values<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>?
        )
        val firstVersion =
            add(3u, "firstVersion", NumberDefinition(type = UInt64), ValuesWithMetaData<*, *>::firstVersion)
        val lastVersion = add(4u, "lastVersion", NumberDefinition(type = UInt64), ValuesWithMetaData<*, *>::lastVersion)
        val isDeleted = add(5u, "isDeleted", BooleanDefinition(), ValuesWithMetaData<*, *>::isDeleted)
    }

    companion object : QueryDataModel<ValuesWithMetaData<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ValuesWithMetaData<*, *>, Properties>) =
            ValuesWithMetaData<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                key = values(1u),
                values = values(2u),
                firstVersion = values(3u),
                lastVersion = values(4u),
                isDeleted = values(5u)
            )
    }
}

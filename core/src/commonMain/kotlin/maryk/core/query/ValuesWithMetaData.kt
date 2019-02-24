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
        val key = add(1, "key", ContextualReferenceDefinition<RequestContext>(
            contextualResolver = {
                it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
            }
        ), ValuesWithMetaData<*, *>::key)

        @Suppress("UNCHECKED_CAST")
        val values = add(2, "values",
            ContextualEmbeddedValuesDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? AbstractValuesDataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, RequestContext>?
                        ?: throw ContextNotFoundException()
                }
            ) as IsEmbeddedValuesDefinition<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions, RequestContext>,
            ValuesWithMetaData<*, *>::values as (ValuesWithMetaData<*, *>) -> Values<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>?
        )
        val firstVersion =
            add(3, "firstVersion", NumberDefinition(type = UInt64), ValuesWithMetaData<*, *>::firstVersion)
        val lastVersion = add(4, "lastVersion", NumberDefinition(type = UInt64), ValuesWithMetaData<*, *>::lastVersion)
        val isDeleted = add(5, "isDeleted", BooleanDefinition(), ValuesWithMetaData<*, *>::isDeleted)
    }

    companion object : QueryDataModel<ValuesWithMetaData<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ValuesWithMetaData<*, *>, Properties>) =
            ValuesWithMetaData<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                key = values(1),
                values = values(2),
                firstVersion = values(3),
                lastVersion = values(4),
                isDeleted = values(5)
            )
    }
}

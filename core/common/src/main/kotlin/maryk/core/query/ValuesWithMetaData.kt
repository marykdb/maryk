package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.AbstractValuesDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.objects.Values
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64

data class ValuesWithMetaData<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val key: Key<DM>,
    val values: Values<DM, P>,
    val firstVersion: UInt64,
    val lastVersion: UInt64,
    val isDeleted: Boolean
) {
    internal companion object: SimpleQueryDataModel<ValuesWithMetaData<*, *>>(
        properties = object : ObjectPropertyDefinitions<ValuesWithMetaData<*, *>>() {
            init {
                add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                    }
                ), ValuesWithMetaData<*, *>::key)

                @Suppress("UNCHECKED_CAST")
                add(1, "values",
                    ContextualEmbeddedValuesDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            it?.dataModel as? AbstractValuesDataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, DataModelPropertyContext>? ?: throw ContextNotFoundException()
                        }
                    ) as IsSerializableFlexBytesEncodable<Values<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>, DataModelPropertyContext>,
                    ValuesWithMetaData<*, *>::values as (ValuesWithMetaData<*, *>) -> Values<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>?
                )
                add(2, "firstVersion", NumberDefinition(type = UInt64), ValuesWithMetaData<*, *>::firstVersion)
                add(3, "lastVersion", NumberDefinition(type = UInt64), ValuesWithMetaData<*, *>::lastVersion)
                add(4, "isDeleted", BooleanDefinition(), ValuesWithMetaData<*, *>::isDeleted)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ValuesWithMetaData<*, *>>) = ValuesWithMetaData<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
            key = map(0),
            values = map(1),
            firstVersion = map(2),
            lastVersion = map(3),
            isDeleted = map(4)
        )
    }
}

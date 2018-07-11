package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualEmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64

data class DataObjectWithMetaData<out DO: Any>(
    val key: Key<DO>,
    val dataObject: DO,
    val firstVersion: UInt64,
    val lastVersion: UInt64,
    val isDeleted: Boolean
) {
    internal companion object: SimpleQueryDataModel<DataObjectWithMetaData<*>>(
        properties = object : ObjectPropertyDefinitions<DataObjectWithMetaData<*>>() {
            init {
                add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel?.key ?: throw ContextNotFoundException()
                    }
                ), DataObjectWithMetaData<*>::key)
                add(1, "dataObject", ContextualEmbeddedObjectDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel ?: throw ContextNotFoundException()
                    }
                ), DataObjectWithMetaData<*>::dataObject)
                add(2, "firstVersion", NumberDefinition(type = UInt64), DataObjectWithMetaData<*>::firstVersion)
                add(3, "lastVersion", NumberDefinition(type = UInt64), DataObjectWithMetaData<*>::lastVersion)
                add(4, "isDeleted", BooleanDefinition(), DataObjectWithMetaData<*>::isDeleted)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<DataObjectWithMetaData<*>>) = DataObjectWithMetaData(
            key = map(0),
            dataObject = map(1),
            firstVersion = map(2),
            lastVersion = map(3),
            isDeleted = map(4)
        )
    }
}

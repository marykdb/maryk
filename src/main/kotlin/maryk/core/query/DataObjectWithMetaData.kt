package maryk.core.query

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.contextual.ContextualSubModelDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64

data class DataObjectWithMetaData<out DO: Any>(
    val key: Key<DO>,
    val dataObject: DO,
    val firstVersion: UInt64,
    val lastVersion: UInt64,
    val isDeleted: Boolean
) {
    internal companion object: QueryDataModel<DataObjectWithMetaData<*>>(
        properties = object : PropertyDefinitions<DataObjectWithMetaData<*>>() {
            init {
                add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = { it!!.dataModel!!.key }
                ), DataObjectWithMetaData<*>::key)
                add(1, "dataObject", ContextualSubModelDefinition<DataModelPropertyContext>(
                    contextualResolver = { it!!.dataModel!! }
                ), DataObjectWithMetaData<*>::dataObject)
                add(2, "firstVersion", NumberDefinition(type = UInt64), DataObjectWithMetaData<*>::firstVersion)
                add(3, "lastVersion", NumberDefinition(type = UInt64), DataObjectWithMetaData<*>::lastVersion)
                add(4, "isDeleted", BooleanDefinition(), DataObjectWithMetaData<*>::isDeleted)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = DataObjectWithMetaData(
            key = map[0] as Key<Any>,
            dataObject = map[1] as Any,
            firstVersion = map[2] as UInt64,
            lastVersion = map[3] as UInt64,
            isDeleted = map[4] as Boolean
        )
    }
}
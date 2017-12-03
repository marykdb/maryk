package maryk.core.query

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.contextual.ContextualSubModelDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.UInt64

data class DataObjectWithMetaData<out DO: Any>(
        val key: Key<DO>,
        val dataObject: DO,
        val firstVersion: UInt64,
        val lastVersion: UInt64,
        val isDeleted: Boolean
) {
    internal object Properties : PropertyDefinitions<DataObjectWithMetaData<*>>() {
        val key = ContextualReferenceDefinition<DataModelPropertyContext>(
                name = "key",
                index = 0,
                contextualResolver = { it!!.dataModel!!.key }
        )
        val dataObject = ContextualSubModelDefinition<DataModelPropertyContext>(
                name = "dataObject",
                index = 1,
                contextualResolver = { it!!.dataModel!! }
        )
        val firstVersion = NumberDefinition(
                name = "firstVersion",
                index = 2,
                required = true,
                type = UInt64
        )
        val lastVersion = NumberDefinition(
                name = "lastVersion",
                index = 3,
                required = true,
                type = UInt64
        )
        val isDeleted = BooleanDefinition(
                name = "isDeleted",
                index = 4,
                required = true
        )
    }

    companion object: QueryDataModel<DataObjectWithMetaData<*>>(
            definitions = listOf(
                    Def(Properties.key, DataObjectWithMetaData<*>::key),
                    Def(Properties.dataObject, DataObjectWithMetaData<*>::dataObject),
                    Def(Properties.firstVersion, DataObjectWithMetaData<*>::firstVersion),
                    Def(Properties.lastVersion, DataObjectWithMetaData<*>::lastVersion),
                    Def(Properties.isDeleted, DataObjectWithMetaData<*>::isDeleted)
            ),
            properties = object : PropertyDefinitions<DataObjectWithMetaData<*>>() {
                init {
                    add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                            contextualResolver = { it!!.dataModel!!.key }
                    ), DataObjectWithMetaData<*>::key)
                    add(1, "dataObject", ContextualSubModelDefinition<DataModelPropertyContext>(
                            contextualResolver = { it!!.dataModel!! }
                    ), DataObjectWithMetaData<*>::dataObject)
                    add(2, "firstVersion", NumberDefinition(
                            required = true,
                            type = UInt64
                    ), DataObjectWithMetaData<*>::firstVersion)
                    add(3, "lastVersion", NumberDefinition(
                            required = true,
                            type = UInt64
                    ),DataObjectWithMetaData<*>::lastVersion)
                    add(4, "isDeleted", BooleanDefinition(required = true), DataObjectWithMetaData<*>::isDeleted)
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
package maryk.core.query.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapReference
import maryk.core.query.DataModelPropertyContext

/** Changes for a map property
 * @param reference to property affected by the change
 * @param valuesToAdd map with values to add to stored map
 * @param keysToDelete set with all keys of items to delete
 * @param valueToCompare (optional) if set the current value is checked against this value.
 * Operation will only complete if they both are equal
 * @param K: type of key to be operated on
 * @param V: type of value to be operated on
 */
data class MapPropertyChange<K: Any, V: Any>(
        override val reference: IsPropertyReference<Map<K, V>, MapDefinition<K, V, *>>,
        val valuesToAdd: Map<K, V>? = null,
        val keysToDelete: Set<K>? = null,
        override val valueToCompare: Map<K, V>? = null
) : IsPropertyOperation<Map<K, V>> {
    override val changeType = ChangeType.MAP_CHANGE

    internal object Properties : PropertyDefinitions<MapPropertyChange<*, *>>() {
        @Suppress("UNCHECKED_CAST")
        private val keyDefinition = ContextualValueDefinition(contextualResolver = { context: DataModelPropertyContext? ->
            (context!!.reference!! as MapReference<Any, Any, IsPropertyContext>).propertyDefinition.keyDefinition
        })
        @Suppress("UNCHECKED_CAST")
        val valueToCompare = ContextualMapDefinition(
                name = "valueToCompare",
                index = 1,
                contextualResolver = { context: DataModelPropertyContext? ->
                    (context!!.reference!! as MapReference<Any, Any, IsPropertyContext>).propertyDefinition
                }
        ) as IsSerializablePropertyDefinition<Map<*, *>, DataModelPropertyContext>
        @Suppress("UNCHECKED_CAST")
        val valuesToAdd = ContextualMapDefinition(
                name = "valuesToAdd",
                index = 2,
                contextualResolver = { context: DataModelPropertyContext? ->
                    (context!!.reference!! as MapReference<Any, Any, IsPropertyContext>).propertyDefinition
                }
        ) as IsSerializablePropertyDefinition<Map<*, *>, DataModelPropertyContext>
        val keysToDelete = SetDefinition(
                name = "keysToDelete",
                index = 3,
                valueDefinition = keyDefinition
        )
    }

    companion object: QueryDataModel<MapPropertyChange<*, *>>(
            definitions = listOf(
                    Def(IsPropertyOperation.Properties.reference, MapPropertyChange<*, *>::reference),
                    Def(Properties.valueToCompare, MapPropertyChange<*, *>::valueToCompare),
                    Def(Properties.valuesToAdd, MapPropertyChange<*, *>::valuesToAdd),
                    Def(Properties.keysToDelete, MapPropertyChange<*, *>::keysToDelete)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = MapPropertyChange(
                reference = map[0] as IsPropertyReference<Map<Any, Any>, MapDefinition<Any, Any, *>>,
                valueToCompare = map[1] as Map<Any, Any>?,
                valuesToAdd = map[2] as Map<Any, Any>?,
                keysToDelete = map[3] as Set<Any>?
        )
    }
}
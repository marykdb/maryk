package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableMap
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/**
 * Changes for a map property containing keys [K] and values [V] referred by [reference]
 * It is possible to add by [valuesToAdd] or to delete with [keysToDelete]
 */
data class MapChange<K: Any, V: Any> internal constructor(
    override val reference: IsPropertyReference<Map<K, V>, MapPropertyDefinitionWrapper<K, V, *, *, *>>,
    val valuesToAdd: Map<K, V>? = null,
    val keysToDelete: Set<K>? = null
) : IsPropertyOperation<Map<K, V>> {
    override val changeType = ChangeType.MapChange

    internal object Properties : PropertyDefinitions<MapChange<out Any, out Any>>() {
        @Suppress("UNCHECKED_CAST")
        private val keyDefinition = ContextualValueDefinition(
            contextualResolver = { context: DataModelPropertyContext? ->
                (context?.reference as MapReference<Any, Any, IsPropertyContext>?)
                    ?.propertyDefinition?.keyDefinition
                        ?: throw ContextNotFoundException()
            }
        )
        @Suppress("UNCHECKED_CAST")
        val valuesToAdd = ContextualMapDefinition(
            required = false,
            contextualResolver = { context: DataModelPropertyContext? ->
                (context?.reference as MapReference<Any, Any, IsPropertyContext>?)
                    ?.propertyDefinition?.definition as IsByteTransportableMap<Any, Any, IsPropertyContext>?
                        ?: throw ContextNotFoundException()
            }
        ) as IsSerializableFlexBytesEncodable<Map<out Any, Any>, DataModelPropertyContext>
        val keysToDelete = SetDefinition(
            required = false,
            valueDefinition = keyDefinition
        )
    }

    internal companion object: QueryDataModel<MapChange<*, *>>(
        properties = object : PropertyDefinitions<MapChange<*, *>>() {
            init {
                DefinedByReference.addReference(this, MapChange<*, *>::reference)
                add(1, "valuesToAdd", Properties.valuesToAdd, MapChange<*, *>::valuesToAdd)
                add(2, "keysToDelete", Properties.keysToDelete, MapChange<*, *>::keysToDelete)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = MapChange<Any, Any>(
            reference = map(0),
            valuesToAdd = map(1),
            keysToDelete = map(2)
        )
    }
}

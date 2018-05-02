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

fun <K: Any, V: Any> IsPropertyReference<Map<K, V>, MapPropertyDefinitionWrapper<K, V, *, *>>.change(
    valuesToAdd: Map<K, V>? = null,
    keysToDelete: Set<K>? = null,
    valueToCompare: Map<K, V>? = null
) = MapPropertyChange(this, valuesToAdd, keysToDelete, valueToCompare)

/**
 * Changes for a map property containing keys [K] and values [V] referred by [reference]
 * It is possible to add by [valuesToAdd] or to delete with [keysToDelete]
 * Optionally compares against [valueToCompare] and will only succeed if values match
 */
data class MapPropertyChange<K: Any, V: Any> internal constructor(
    override val reference: IsPropertyReference<Map<K, V>, MapPropertyDefinitionWrapper<K, V, *, *>>,
    val valuesToAdd: Map<K, V>? = null,
    val keysToDelete: Set<K>? = null,
    override val valueToCompare: Map<K, V>? = null
) : IsPropertyOperation<Map<K, V>> {
    override val changeType = ChangeType.MapChange

    internal object Properties : PropertyDefinitions<MapPropertyChange<out Any, out Any>>() {
        @Suppress("UNCHECKED_CAST")
        private val keyDefinition = ContextualValueDefinition(
            contextualResolver = { context: DataModelPropertyContext? ->
                (context?.reference as MapReference<Any, Any, IsPropertyContext>?)
                    ?.propertyDefinition?.keyDefinition
                        ?: throw ContextNotFoundException()
            }
        )
        @Suppress("UNCHECKED_CAST")
        val valueToCompare = ContextualMapDefinition(
            required = false,
            contextualResolver = { context: DataModelPropertyContext? ->
                (context?.reference as MapReference<Any, Any, IsPropertyContext>?)
                    ?.propertyDefinition?.definition as IsByteTransportableMap<Any, Any, IsPropertyContext>?
                        ?: throw ContextNotFoundException()
            }
        ) as IsSerializableFlexBytesEncodable<Map<out Any, Any>, DataModelPropertyContext>
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

    internal companion object: QueryDataModel<MapPropertyChange<*, *>>(
        properties = object : PropertyDefinitions<MapPropertyChange<*, *>>() {
            init {
                IsPropertyOperation.addReference(this, MapPropertyChange<*, *>::reference)
                add(1, "valueToCompare", Properties.valueToCompare, MapPropertyChange<*, *>::valueToCompare)
                add(2, "valuesToAdd", Properties.valuesToAdd, MapPropertyChange<*, *>::valuesToAdd)
                add(3, "keysToDelete", Properties.keysToDelete, MapPropertyChange<*, *>::keysToDelete)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = MapPropertyChange<Any, Any>(
            reference = map(0),
            valueToCompare = map(1),
            valuesToAdd = map(2),
            keysToDelete = map(3)
        )
    }
}

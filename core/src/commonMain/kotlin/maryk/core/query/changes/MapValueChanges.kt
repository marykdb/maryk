package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsByteTransportableMap
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/**
 * Changes for a map property containing keys [K] and values [V] referred by [reference]
 * It is possible to add by [valuesToAdd] or to delete with [keysToDelete]
 * First keys will be deleted before values are added
 */
data class MapValueChanges<K: Any, V: Any> internal constructor(
    override val reference: IsPropertyReference<Map<K, V>, MapPropertyDefinitionWrapper<K, V, *, *, *>, *>,
    val valuesToAdd: Map<K, V>? = null,
    val keysToDelete: Set<K>? = null
) : DefinedByReference<Map<K, V>> {
    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<MapValueChanges<out Any, out Any>>() {
        val reference = DefinedByReference.addReference(this, MapValueChanges<*, *>::reference)

        @Suppress("UNCHECKED_CAST")
        val valuesToAdd = add(2, "valuesToAdd",
            ContextualMapDefinition(
                required = false,
                contextualResolver = { context: RequestContext? ->
                    (context?.reference as MapReference<Any, Any, IsPropertyContext>?)
                        ?.propertyDefinition?.definition as IsByteTransportableMap<Any, Any, IsPropertyContext>?
                            ?: throw ContextNotFoundException()
                }
            ) as IsSerializableFlexBytesEncodable<Map<out Any, Any>, RequestContext>,
            MapValueChanges<*, *>::valuesToAdd
        )

        @Suppress("UNCHECKED_CAST")
        val keysToDelete = add(3, "keysToDelete",
            SetDefinition(
                required = false,
                valueDefinition = ContextualValueDefinition(
                    contextualResolver = { context: RequestContext? ->
                        (context?.reference as MapReference<Any, Any, IsPropertyContext>?)
                            ?.propertyDefinition?.keyDefinition
                                ?: throw ContextNotFoundException()
                    }
                )
            ),
            MapValueChanges<*, *>::keysToDelete
        )
    }

    companion object: QueryDataModel<MapValueChanges<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<MapValueChanges<*, *>, Properties>) = MapValueChanges<Any, Any>(
            reference = values(1),
            valuesToAdd = values(2),
            keysToDelete = values(3)
        )
    }
}


/**
 * Convenience infix method to define an map value change
 * It is possible to add by [valuesToAdd] or to delete with [keysToDelete]
 * First keys will be deleted before values are added
 */
fun <K: Any, V: Any> IsPropertyReference<Map<K, V>, MapPropertyDefinitionWrapper<K, V, *, *, *>, *>.change(
    valuesToAdd: Map<K, V>? = null,
    keysToDelete: Set<K>? = null
) =
    MapValueChanges(
        reference = this,
        keysToDelete = keysToDelete,
        valuesToAdd = valuesToAdd
    )

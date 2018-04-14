package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference

/**
 * Contains a Map property [definition] which contains keys [K] and values [V]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class MapPropertyDefinitionWrapper<K: Any, V: Any, CX: IsPropertyContext, in DO: Any> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: MapDefinition<K, V, CX>,
    override val getter: (DO) -> Map<K, V>?
) :
    IsMapDefinition<K, V, CX> by definition,
    IsPropertyDefinitionWrapper<Map<K,V>, CX, DO>
{
    override fun getRef(parentRef: IsPropertyReference<*, *>?): MapReference<K, V, CX> =
        MapReference(this, parentRef as CanHaveComplexChildReference<*, *, *>?)

    /** Get a reference to a specific map [key] with optional [parentRef] */
    private fun getKeyRef(key: K, parentRef: IsPropertyReference<*, *>? = null) =
        this.definition.getKeyRef(key, this.getRef(parentRef))

    /** Get a reference to a specific map value by [key] with optional [parentRef] */
    private fun getValueRef(key: K, parentRef: IsPropertyReference<*, *>? = null) =
        this.definition.getValueRef(key, this.getRef(parentRef))

    /** For quick notation to get a map [key] reference */
    infix fun key(key: K): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> MapKeyReference<K, V, *> {
        return { this.getKeyRef(key, it) }
    }

    /** For quick notation to get a map value reference at given [key] */
    infix fun at(key: K): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> MapValueReference<K, V, *> {
        return { this.getValueRef(key, it) }
    }
}

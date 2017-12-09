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

/** Wraps a map definition to contain the context on how it relates to DataObject
 * @param index: of definition to encode into protobuf
 * @param name: of definition to display in human readable format
 * @param definition: to be wrapped for DataObject
 * @param getter: to get property value on a DataObject
 *
 * @param K: type of key property for map
 * @param V: type of value property for map
 * @param CX: Context type for property
 * @param DO: Type of DataObject which contains this property
 */
data class MapPropertyDefinitionWrapper<K: Any, V: Any, CX: IsPropertyContext, in DO: Any>(
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

    /** Get a reference to a specific map key
     * @param key to get reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getKeyRef(key: K, parentRef: IsPropertyReference<*, *>? = null)
            = this.definition.getKeyRef(key, this.getRef(parentRef))

    /** Get a reference to a specific map value by key
     * @param key to get reference to value for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getValueRef(key: K, parentRef: IsPropertyReference<*, *>? = null)
            = this.definition.getValueRef(key, this.getRef(parentRef))

    /** For quick notation to get a map key reference
     * @param key to get reference for
     */
    infix fun key(key: K): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> MapKeyReference<K, V, *> {
        return { this.getKeyRef(key, it) }
    }

    /** For quick notation to get a map value reference at given key
     * @param key to get reference for value
     */
    infix fun at(key: K): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> MapValueReference<K, V, *> {
        return { this.getValueRef(key, it) }
    }
}
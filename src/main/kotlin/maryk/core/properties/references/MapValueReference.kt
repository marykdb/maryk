package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition

/** Reference to a map value by a key
 * @param key             key of property reference
 * @param parentReference reference to parent
 * @param <K> key type
 * @param <V> value type
 */
class MapValueReference<K: Any, V: Any>(
        val key: K,
        parentReference: PropertyReference<Map<K, V>, MapDefinition<K, V>>
) : CanHaveComplexChildReference<V, IsPropertyDefinition<V>>(
        parentReference.propertyDefinition.valueDefinition, parentReference
), EmbeddedPropertyReference<V> {

    override val name = parentReference.name

    override val completeName get() = "${this.parentReference!!.completeName}[$key]"
}
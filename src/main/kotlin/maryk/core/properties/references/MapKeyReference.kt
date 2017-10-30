package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition

/** Reference to a specific Map key
 * @param key             key of property reference
 * @param parentReference reference to parent
 * @param <K> key
 * @param <V> value
 */
class MapKeyReference<K: Any, V: Any>(
        val key: K,
        parentReference: PropertyReference<Map<K,V>, MapDefinition<K, V, *>>
) : CanHaveSimpleChildReference<K, IsPropertyDefinition<K>>(
        parentReference.propertyDefinition.keyDefinition, parentReference
), EmbeddedPropertyReference<K> {
    override val name = parentReference.name

    override val completeName get() = "${this.parentReference!!.completeName}<$key>"
}
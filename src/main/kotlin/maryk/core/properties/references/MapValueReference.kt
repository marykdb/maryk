package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/** Reference to a map value by a key
 * @param key             key of property reference
 * @param parentReference reference to parent
 * @param <K> key type
 * @param <V> value type
 */
class MapValueReference<K: Any, V: Any>(
        val key: K,
        parentReference: MapReference<K, V>
) : CanHaveComplexChildReference<V, IsPropertyDefinition<V>, MapReference<K, V>>(
        parentReference.propertyDefinition.valueDefinition, parentReference
) {

    override val name = parentReference.name

    override val completeName get() = "${this.parentReference!!.completeName}.@$key"

    override fun calculateTransportByteLength(lengthCacher: (length: ByteLengthContainer) -> Unit): Int {
        val parentLength = this.parentReference!!.calculateTransportByteLength(lengthCacher)
        val valueLength = this.parentReference.propertyDefinition.keyDefinition.calculateTransportByteLength(key, lengthCacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(lengthCacheGetter, writer)
        ProtoBuf.writeKey(0, WireType.VAR_INT, writer)
        this.parentReference!!.propertyDefinition.keyDefinition.writeTransportBytes(key, lengthCacheGetter, writer)
    }
}
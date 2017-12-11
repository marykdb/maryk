package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Reference to a specific Map key
 * @param key             key of property reference
 * @param parentReference reference to parent
 * @param <K> key
 * @param <V> value
 */
class MapKeyReference<K: Any, V: Any, CX: IsPropertyContext>(
        val key: K,
        mapDefinition: MapDefinition<K, V, CX>,
        parentReference: MapReference<K, V, CX>?
) : CanHaveSimpleChildReference<K, IsPropertyDefinition<K>, MapReference<K, V, CX>>(
        mapDefinition.keyDefinition, parentReference
) {
    override val completeName get() = this.parentReference?.let {
        "${it.completeName}.$$key"
    } ?: "$$key"

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference!!.calculateTransportByteLength(cacher)
        val valueLength = this.parentReference.propertyDefinition.keyDefinition.calculateTransportByteLength(key, cacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(1, WireType.VAR_INT, writer)
        this.parentReference!!.propertyDefinition.keyDefinition.writeTransportBytes(key, cacheGetter, writer)
    }
}
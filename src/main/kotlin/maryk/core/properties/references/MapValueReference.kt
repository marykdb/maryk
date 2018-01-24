package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Reference to a map value [V] by a [key] of [K] contained in map referred by [parentReference]
 */
class MapValueReference<K: Any, V: Any, CX: IsPropertyContext>(
    val key: K,
    mapDefinition: MapDefinition<K, V, CX>,
    parentReference: MapReference<K, V, CX>?
) : CanHaveComplexChildReference<V, IsPropertyDefinition<V>, MapReference<K, V, CX>>(
    mapDefinition.valueDefinition, parentReference
) {
    override val completeName get() = this.parentReference?.let {
        "${it.completeName}.@$key"
    } ?: "@$key"

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference!!.calculateTransportByteLength(cacher)
        val valueLength = this.parentReference.propertyDefinition.keyDefinition.calculateTransportByteLength(key, cacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0, WireType.VAR_INT, writer)
        this.parentReference!!.propertyDefinition.keyDefinition.writeTransportBytes(key, cacheGetter, writer)
    }
}
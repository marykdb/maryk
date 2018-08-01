package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Reference to map value [V] by [key] of [K] contained in map referred by [parentReference] */
class MapValueReference<K: Any, V: Any, CX: IsPropertyContext> internal constructor(
    val key: K,
    private val mapDefinition: MapDefinition<K, V, CX>,
    parentReference: MapReference<K, V, CX>?
) : CanHaveComplexChildReference<V, IsPropertyDefinition<V>, MapReference<K, V, CX>, Map<K, V>>(
    mapDefinition.valueDefinition, parentReference
) {
    override val completeName get() = this.parentReference?.let {
        "${it.completeName}.@$key"
    } ?: "@$key"

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        val valueLength = mapDefinition.keyDefinition.calculateTransportByteLength(key, cacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0, WireType.VAR_INT, writer)
        mapDefinition.keyDefinition.writeTransportBytes(key, cacheGetter, writer)
    }

    override fun resolve(values: Map<K, V>): V? {
        return values[key]
    }
}

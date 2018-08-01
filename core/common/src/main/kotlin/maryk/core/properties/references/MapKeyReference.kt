package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Reference to a specific Map [key] of [K] to value [V] contained in map referred by [parentReference] */
class MapKeyReference<K: Any, V: Any, CX: IsPropertyContext> internal constructor(
    val key: K,
    private val mapDefinition: MapDefinition<K, V, CX>,
    parentReference: MapReference<K, V, CX>?
) : CanHaveSimpleChildReference<K, IsPropertyDefinition<K>, MapReference<K, V, CX>, Map<K, V>>(
    mapDefinition.keyDefinition, parentReference
) {
    override val completeName get() = this.parentReference?.let {
        "${it.completeName}.$$key"
    } ?: "$$key"

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        val valueLength = mapDefinition.keyDefinition.calculateTransportByteLength(key, cacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(1, WireType.VAR_INT, writer)
        mapDefinition.keyDefinition.writeTransportBytes(key, cacheGetter, writer)
    }

    override fun resolve(values: Map<K, V>): K? {
        return key
    }
}

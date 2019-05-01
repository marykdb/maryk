package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Reference to a specific Map [key] of [K] containing value [V] contained in map referred by [parentReference] */
class MapKeyReference<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    val key: K,
    private val mapDefinition: IsMapDefinition<K, V, CX>,
    parentReference: MapReference<K, V, CX>?
) : CanHaveSimpleChildReference<K, IsPropertyDefinition<K>, MapReference<K, V, CX>, Map<K, V>>(
        mapDefinition.keyDefinition, parentReference
    ),
    IsPropertyReferenceWithParent<K, IsPropertyDefinition<K>, MapReference<K, V, CX>, Map<K, V>> {
    override val completeName
        get() = this.parentReference?.let {
            "${it.completeName}.$$key"
        } ?: "$$key"

    override fun resolveFromAny(value: Any): Any {
        @Suppress("UNCHECKED_CAST")
        val map = (value as? Map<K, V>) ?: throw UnexpectedValueException("Expected Map to get value by reference")
        if (map.containsKey(this.key)) {
            return this.key
        } else throw UnexpectedValueException("Expected Map to contain key to get by reference")
    }

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        val valueLength = mapDefinition.keyDefinition.calculateTransportByteLength(key, cacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(1u, VAR_INT, writer)
        mapDefinition.keyDefinition.writeTransportBytes(key, cacheGetter, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        throw NotImplementedError("Map Key reference is not supported to convert to storage bytes.")
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        throw NotImplementedError("Map Key reference is not supported to convert to storage bytes.")
    }

    override fun resolve(values: Map<K, V>): K? {
        return key
    }
}

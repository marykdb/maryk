package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Reference to a specific Map [key] of [K] containing value [V] contained in map referred by [parentReference] */
class MapKeyReference<K: Any, V: Any, CX: IsPropertyContext> internal constructor(
    val key: K,
    private val mapDefinition: IsMapDefinition<K, V, CX>,
    parentReference: MapReference<K, V, CX>?
) : CanHaveSimpleChildReference<K, IsPropertyDefinition<K>, MapReference<K, V, CX>, Map<K, V>>(
    mapDefinition.keyDefinition, parentReference
) {
    override val completeName get() = this.parentReference?.let {
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
        ProtoBuf.writeKey(1, WireType.VAR_INT, writer)
        mapDefinition.keyDefinition.writeTransportBytes(key, cacheGetter, writer)
    }

    override fun calculateStorageByteLength(): Int {
        val parentCount = this.parentReference?.parentReference?.calculateStorageByteLength() ?: 0
        return parentCount +
                1 + // The type byte
                // The map index
                (this.parentReference?.propertyDefinition?.index?.calculateVarByteLength() ?: 0) +
                // The map key
                this.mapDefinition.keyDefinition.calculateStorageByteLength(this.key)
    }

    override fun writeStorageBytes(writer: (byte: Byte) -> Unit) {
        this.parentReference?.parentReference?.writeStorageBytes(writer)

        writer(CompleteReferenceType.MAP_KEY.value)
        this.parentReference?.propertyDefinition?.index?.writeVarBytes(writer)

        this.mapDefinition.keyDefinition.writeStorageBytes(this.key, writer)
    }

    override fun resolve(values: Map<K, V>): K? {
        return key
    }
}

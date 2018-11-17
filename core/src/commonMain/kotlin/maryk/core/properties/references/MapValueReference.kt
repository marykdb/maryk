package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Reference to map value [V] below [key] of [K] contained in map referred by [parentReference] */
class MapValueReference<K: Any, V: Any, CX: IsPropertyContext> internal constructor(
    val key: K,
    private val mapDefinition: IsMapDefinition<K, V, CX>,
    parentReference: MapReference<K, V, CX>?
) : CanHaveComplexChildReference<V, IsPropertyDefinition<V>, MapReference<K, V, CX>, Map<K, V>>(
    mapDefinition.valueDefinition, parentReference
) {
    override val completeName get() = this.parentReference?.let {
        "${it.completeName}.@$key"
    } ?: "@$key"

    override fun resolveFromAny(value: Any): Any {
        @Suppress("UNCHECKED_CAST")
        val map = (value as? Map<K, V>) ?: throw UnexpectedValueException("Expected Map to get value by reference")
        return map[this.key] as Any? ?: throw UnexpectedValueException("Expected Map to contain key to get value by reference")
    }

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

    override fun calculateStorageByteLength(): Int {
        // Calculate bytes above the setReference parent
        val parentCount = this.parentReference?.parentReference?.calculateStorageByteLength() ?: 0

        return parentCount +
                // calculate length of index of setDefinition
                (this.parentReference?.propertyDefinition?.index?.calculateVarIntWithExtraInfoByteSize() ?: 0) +
                // add bytes for map key
                @Suppress("UNCHECKED_CAST")
                (mapDefinition.keyDefinition as IsFixedBytesEncodable<K>).calculateStorageByteLength(key)
    }

    override fun writeStorageBytes(writer: (byte: Byte) -> Unit) {
        // Calculate bytes above the setReference parent
        this.parentReference?.parentReference?.writeStorageBytes(writer)
        // Write set index with a SetValue type
        this.parentReference?.propertyDefinition?.index?.writeVarIntWithExtraInfo(MAP.value, writer)
        // Write value bytes
        @Suppress("UNCHECKED_CAST")
        (mapDefinition.keyDefinition as IsFixedBytesEncodable<K>).writeStorageBytes(key, writer)
    }

    override fun resolve(values: Map<K, V>): V? {
        return values[key]
    }
}

package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.protobuf.calculateKeyAndContentLength
import maryk.core.protobuf.writeKeyWithLength

/** Reference to a specific Map [key] of [K] containing value [V] contained in map referred by [parentReference] */
class MapKeyReference<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    val key: K,
    private val mapDefinition: IsMapDefinition<K, V, CX>,
    parentReference: CanContainMapItemReference<*, *, *>?
) : CanHaveSimpleChildReference<K, IsPropertyDefinition<K>, CanContainMapItemReference<*, *, *>, Map<K, V>>(
        mapDefinition.keyDefinition, parentReference
    ),
    CanContainMapItemReference<K, IsPropertyDefinition<K>, Map<K, V>>,
    IsPropertyReferenceWithParent<K, IsPropertyDefinition<K>, CanContainMapItemReference<*, *, *>, Map<K, V>> {
    override val completeName by lazy {
        this.parentReference?.let {
            "${it.completeName}.#$key"
        } ?: "#$key"
    }

    override fun resolveFromAny(value: Any): Any {
        @Suppress("UNCHECKED_CAST")
        val map = (value as? Map<K, V>) ?: throw UnexpectedValueException("Expected Map to get value by reference")
        if (map.containsKey(this.key)) {
            return this.key
        } else throw UnexpectedValueException("Expected Map to contain key to get by reference")
    }

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + calculateKeyAndContentLength(mapDefinition.keyDefinition.wireType, 1u, cacher) {
            mapDefinition.keyDefinition.calculateTransportByteLength(key, cacher)
        }
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        writeKeyWithLength(mapDefinition.keyDefinition.wireType, 1u, writer, cacheGetter)
        mapDefinition.keyDefinition.writeTransportBytes(key, cacheGetter, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        throw NotImplementedError("Map Key reference is not supported to convert to storage bytes.")
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        throw NotImplementedError("Map Key reference is not supported to convert to storage bytes.")
    }

    override fun resolve(values: Map<K, V>): K = key
}

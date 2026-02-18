package maryk.core.properties.references

import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.matchers.FuzzyDynamicLengthMatch
import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.processors.datastore.matchers.IsFuzzyMatcher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.SubListDefinition
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.index.toReferenceStorageByteArray
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.IsValuesGetter

/** Reference to map key [K] below any key contained in map referred by [parentReference] */
class MapAnyKeyReference<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    val mapDefinition: IsMapDefinition<K, V, CX>,
    parentReference: CanContainMapItemReference<*, *, *>?
) : IsFuzzyReference,
    IsIndexablePropertyReference<K>,
    IsPropertyReferenceWithParent<List<K>, IsListDefinition<K, CX>, CanContainMapItemReference<*, *, *>, Map<K, V>>,
    CanHaveComplexChildReference<List<K>, IsListDefinition<K, CX>, CanContainMapItemReference<*, *, *>, Map<K, V>>(
        SubListDefinition(valueDefinition = mapDefinition.keyDefinition as IsValueDefinition<K, CX>),
        parentReference
    ) {
    override val indexKeyPartType = IndexKeyPartType.Reference
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    override val completeName by lazy {
        this.parentReference?.let {
            "${it.completeName}.~"
        } ?: "~"
    }

    /** Convenience infix method to create Reference [value] pairs */
    @Suppress("UNCHECKED_CAST")
    infix fun <T : Any> with(value: T) =
        ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)

    override fun fuzzyMatcher(): IsFuzzyMatcher {
        val keyDefinition = mapDefinition.keyDefinition
        return if (keyDefinition is IsFixedStorageBytesEncodable<*>) {
            FuzzyExactLengthMatch(keyDefinition.byteSize)
        } else {
            FuzzyDynamicLengthMatch
        }
    }

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(4u, VAR_INT, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        throw NotImplementedError("Map Any Key is not supported to convert to storage bytes. It uses fuzzy matchers instead")
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        throw NotImplementedError("Map Any Key is not supported to convert to storage bytes. It uses fuzzy matchers instead")
    }

    @Suppress("UNCHECKED_CAST")
    private val bytesDefinition: IsStorageBytesEncodable<K>
        get() = mapDefinition.keyDefinition as? IsStorageBytesEncodable<K>
            ?: throw RequestException("Map Any Key only supports indexing on storage-encodable key definitions")

    override fun getValue(values: IsValuesGetter): K =
        getValues(values).firstOrNull() ?: throw RequiredException(this)

    override fun toStorageByteArrays(values: IsValuesGetter): List<ByteArray> {
        val valueBytes = LinkedHashSet<Bytes>()
        for (value in getValues(values)) {
            val length = bytesDefinition.calculateStorageByteLength(value)
            val output = ByteArray(length)
            var index = 0
            bytesDefinition.writeStorageBytes(value) { output[index++] = it }
            valueBytes += Bytes(output)
        }
        return valueBytes.map { it.bytes }
    }

    override fun calculateStorageByteLength(value: K): Int =
        bytesDefinition.calculateStorageByteLength(value)

    override fun writeStorageBytes(value: K, writer: (byte: Byte) -> Unit) =
        bytesDefinition.writeStorageBytes(value, writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte): K =
        bytesDefinition.readStorageBytes(length, reader)

    override fun isForPropertyReference(propertyReference: AnyPropertyReference): Boolean =
        propertyReference == this

    override fun toQualifierStorageByteArray() = parentReference?.toStorageByteArray()

    override fun calculateReferenceStorageByteLength(): Int {
        val parentLength = parentReference?.calculateStorageByteLength() ?: 0
        val markerLength = marker.size.calculateVarByteLength()
        return parentLength + markerLength + marker.size
    }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        parentReference?.writeStorageBytes(writer)
        marker.size.writeVarBytes(writer)
        marker.forEach(writer)
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel): Boolean =
        dataModel.compatibleWithReference(this)

    private fun getValues(values: IsValuesGetter): List<K> {
        @Suppress("UNCHECKED_CAST")
        val map = values[parentReference as AnyPropertyReference] as? Map<K, V>
            ?: throw RequiredException(parentReference)
        return map.keys.toList()
    }

    override fun resolve(values: Map<K, V>): List<K> = values.keys.toMutableList()

    override fun resolveFromAny(value: Any): Any =
        if (value is Map<*, *>) {
            value.keys.toMutableList()
        } else {
            throw RequestException("Expected a map into resolveFromAny instead of $value")
        }

    override fun equals(other: Any?) =
        this === other || (other is MapAnyKeyReference<*, *, *> && parentReference == other.parentReference)

    override fun hashCode() = 31 * (parentReference?.hashCode() ?: 0) + marker[0]

    private companion object {
        val marker = byteArrayOf(43)
    }
}

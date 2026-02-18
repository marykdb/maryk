package maryk.core.properties.references

import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.matchers.FuzzyDynamicLengthMatch
import maryk.core.processors.datastore.matchers.IsFuzzyMatcher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
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

/** Reference to any value contained in set referred by [parentReference] */
class SetAnyValueReference<T : Any, CX : IsPropertyContext> internal constructor(
    val setDefinition: IsSetDefinition<T, CX>,
    parentReference: CanContainSetItemReference<*, *, *>?
) : IsFuzzyReference,
    IsIndexablePropertyReference<T>,
    IsPropertyReferenceWithParent<List<T>, IsListDefinition<T, CX>, CanContainSetItemReference<*, *, *>, Set<T>>,
    CanHaveComplexChildReference<List<T>, IsListDefinition<T, CX>, CanContainSetItemReference<*, *, *>, Set<T>>(
        SubListDefinition(valueDefinition = setDefinition.valueDefinition),
        parentReference
    ) {
    override val indexKeyPartType = IndexKeyPartType.Reference
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    override val completeName by lazy {
        this.parentReference?.let {
            "${it.completeName}.*"
        } ?: "*"
    }

    /** Convenience infix method to create Reference [value] pairs */
    @Suppress("UNCHECKED_CAST")
    infix fun <T : Any> with(value: T) =
        ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)

    override fun fuzzyMatcher(): IsFuzzyMatcher = FuzzyDynamicLengthMatch

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(1u, VAR_INT, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        throw NotImplementedError("Set Any Value is not supported to convert to storage bytes. It uses fuzzy matchers instead")
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        throw NotImplementedError("Set Any Value is not supported to convert to storage bytes. It uses fuzzy matchers instead")
    }

    @Suppress("UNCHECKED_CAST")
    private val bytesDefinition: IsStorageBytesEncodable<T>
        get() = setDefinition.valueDefinition as? IsStorageBytesEncodable<T>
            ?: throw RequestException("Set Any Value only supports indexing on storage-encodable value definitions")

    override fun getValue(values: IsValuesGetter): T =
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

    override fun calculateStorageByteLength(value: T): Int =
        bytesDefinition.calculateStorageByteLength(value)

    override fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit) =
        bytesDefinition.writeStorageBytes(value, writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte): T =
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

    private fun getValues(values: IsValuesGetter): List<T> {
        @Suppress("UNCHECKED_CAST")
        val set = values[parentReference as AnyPropertyReference] as? Set<T>
            ?: throw RequiredException(parentReference)
        return set.toList()
    }

    override fun resolve(values: Set<T>): List<T> = values.toMutableList()

    override fun resolveFromAny(value: Any): Any =
        if (value is Set<*>) {
            value.toList()
        } else {
            throw RequestException("Expected a set into resolveFromAny instead of $value")
        }

    override fun equals(other: Any?) =
        this === other || (other is SetAnyValueReference<*, *> && parentReference == other.parentReference)

    override fun hashCode() = 31 * (parentReference?.hashCode() ?: 0) + marker[0]

    private companion object {
        val marker = byteArrayOf(42)
    }
}

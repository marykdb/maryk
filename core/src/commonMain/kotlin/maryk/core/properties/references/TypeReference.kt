package maryk.core.properties.references

import maryk.core.exceptions.StorageException
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.toReferenceStorageByteArray
import maryk.core.properties.enum.IsIndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.values.IsValuesGetter

/** Reference to any MultiType reference */
data class TypeReference<E : TypeEnum<T>, T: Any, in CX : IsPropertyContext> internal constructor(
    val multiTypeDefinition: IsMultiTypeDefinition<E, T, CX>,
    override val parentReference: CanHaveComplexChildReference<TypedValue<E, T>, IsMultiTypeDefinition<E, T, *>, *, *>?,
) : IsPropertyReferenceWithParent<E, IsIndexedEnumDefinition<E>, CanHaveComplexChildReference<TypedValue<E, T>, IsMultiTypeDefinition<E, T, *>, *, *>, TypedValue<E, T>>,
    IsFixedBytesPropertyReference<E>,
    IsFixedStorageBytesEncodable<E> by multiTypeDefinition.typeEnum,
    IsIndexable
{
    override val indexKeyPartType = IndexKeyPartType.Reference
    override val propertyDefinition = multiTypeDefinition.typeEnum
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    override val completeName
        get() = this.parentReference?.let {
            "${it.completeName}.*"
        } ?: "*"

    override fun resolveFromAny(value: Any): Any {
        @Suppress("UNCHECKED_CAST")
        return (value as? TypedValue<E, *>)?.type ?: (value as? E)
            ?: throw UnexpectedValueException("Expected TypedValue to get id by reference")
    }

    override fun getValue(values: IsValuesGetter): E {
        @Suppress("UNCHECKED_CAST")
        val typedValue = values[parentReference as IsPropertyReference<Any, *, *>]
            ?: throw RequiredException(parentReference)
        @Suppress("UNCHECKED_CAST")
        return if (typedValue is MultiTypeEnum<*>) {
            typedValue as E
        } else if (typedValue is TypedValue<*, *>) {
            typedValue.type as E
        } else throw StorageException("Unknown type for $typedValue")
    }

    override fun isForPropertyReference(propertyReference: AnyPropertyReference): Boolean {
        return propertyReference == this
    }

    override fun toQualifierStorageByteArray() = this.toStorageByteArray()

    override fun calculateReferenceStorageByteLength(): Int {
        val refLength = this.parentReference?.calculateStorageByteLength() ?: 0
        return refLength.calculateVarIntWithExtraInfoByteSize() + refLength
    }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        val refLength = this.parentReference?.calculateStorageByteLength() ?: 0
        refLength.writeVarIntWithExtraInfo(
            this.indexKeyPartType.index.toByte(),
            writer
        )
        this.parentReference?.writeStorageBytes(writer)
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel) =
        dataModel.compatibleWithReference(this)

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1 + 1 // Last is for length of type bytes
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0u, VAR_INT, writer)
        0.writeVarBytes(writer)
    }

    override fun calculateSelfStorageByteLength() = 0 // For length of type bytes

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        // This is used within comparisons with storage
        // Writes nothing since the type is stored below the parent reference. So any type checks should run on the parent storage key.
    }

    override fun resolve(values: TypedValue<E, T>): E = values.type
}

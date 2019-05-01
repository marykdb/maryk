package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.references.ReferenceType.TYPE
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.values.IsValuesGetter

/** Reference to any MultiType reference */
data class TypeReference<E : IndexedEnum, in CX : IsPropertyContext> internal constructor(
    val multiTypeDefinition: IsMultiTypeDefinition<E, CX>,
    override val parentReference: CanHaveComplexChildReference<TypedValue<E, *>, IsMultiTypeDefinition<E, *>, *, *>?
) : IsPropertyReferenceWithParent<E, IndexedEnumDefinition<E>, CanHaveComplexChildReference<TypedValue<E, *>, IsMultiTypeDefinition<E, *>, *, *>, TypedValue<E, *>>,
    IsFixedBytesPropertyReference<E>,
    IsFixedStorageBytesEncodable<E> by multiTypeDefinition.typeEnum,
    IsIndexable
{
    override val indexKeyPartType = IndexKeyPartType.Reference
    override val propertyDefinition = multiTypeDefinition.typeEnum

    override val completeName
        get() = this.parentReference?.let {
            "${it.completeName}.*"
        } ?: "*"

    override fun resolveFromAny(value: Any): Any {
        @Suppress("UNCHECKED_CAST")
        return (value as? TypedValue<E, *>)?.type
            ?: throw UnexpectedValueException("Expected TypedValue to get id by reference")
    }

    override fun getValue(values: IsValuesGetter): E {
        val typedValue: TypedValue<E, *> = values[parentReference as IsPropertyReference<TypedValue<E, *>, IsPropertyDefinition<TypedValue<E, *>>, *>]
            ?: throw RequiredException(parentReference)
        return typedValue.type
    }

    override fun isForPropertyReference(propertyReference: AnyPropertyReference): Boolean {
        return propertyReference == parentReference
    }

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

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1 + 1 // Last is for length of type bytes
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0u, VAR_INT, writer)
        0.writeVarBytes(writer)
    }

    override fun calculateSelfStorageByteLength() = 1 // For length of type bytes

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        // Write type index bytes
        0.writeVarIntWithExtraInfo(
            TYPE.value,
            writer
        )
    }

    override fun resolve(values: TypedValue<E, *>): E? {
        return values.type
    }
}

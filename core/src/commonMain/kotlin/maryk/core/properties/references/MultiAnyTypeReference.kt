package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Reference to any MultiType reference */
data class MultiAnyTypeReference<E : IndexedEnum<E>, in CX : IsPropertyContext> internal constructor(
    val multiTypeDefinition: IsMultiTypeDefinition<E, CX>,
    val parentReference: AnyPropertyReference?
) : IsPropertyReference<E, IndexedEnumDefinition<E>, TypedValue<E, *>> {
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

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1 + 1 // Last is for length of type bytes
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0, WireType.VAR_INT, writer)
        0.writeVarBytes(writer)
    }

    override fun calculateStorageByteLength(): Int {
        val parentCount = this.parentReference?.calculateStorageByteLength() ?: 0
        return parentCount + 1 // Last is for length of type bytes
    }

    override fun writeStorageBytes(writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeStorageBytes(writer)

        // Write type index bytes
        0.writeVarIntWithExtraInfo(
            CompleteReferenceType.TYPE.value,
            writer
        )
    }

    override fun resolve(values: TypedValue<E, *>): E? {
        return values.type
    }
}

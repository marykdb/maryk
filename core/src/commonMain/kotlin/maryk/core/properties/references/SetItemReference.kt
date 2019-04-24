package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsBytesEncodable
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Reference to a Set Item by [value] of [T] and context [CX] on set referred to [parentReference] and
 * defined by [setDefinition]
 */
class SetItemReference<T : Any, CX : IsPropertyContext> internal constructor(
    val value: T,
    val setDefinition: IsSetDefinition<T, CX>,
    parentReference: SetReference<T, CX>?
) : CanHaveSimpleChildReference<T, IsValueDefinition<T, CX>, SetReference<T, CX>, Set<T>>(
        setDefinition.valueDefinition, parentReference
    ),
    IsPropertyReferenceWithIndirectStorageParent<T, IsValueDefinition<T, CX>, SetReference<T, CX>, Set<T>> {
    override val completeName: String
        get() = this.parentReference?.let {
            "${it.completeName}.$$value"
        } ?: "$$value"

    override fun resolveFromAny(value: Any) =
        if (value is Set<*> && value.contains(this.value)) {
            this.value
        } else {
            throw UnexpectedValueException("Expected List to get value by reference")
        }

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        val valueLength = setDefinition.valueDefinition.calculateTransportByteLength(value, cacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0u, VAR_INT, writer)
        setDefinition.valueDefinition.writeTransportBytes(value, cacheGetter, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        @Suppress("UNCHECKED_CAST")
        val setItemLength = (this.setDefinition.valueDefinition as IsBytesEncodable<T>).calculateStorageByteLength(this.value)

        // calculate length of index of setDefinition
        return (this.parentReference?.propertyDefinition?.index?.calculateVarIntWithExtraInfoByteSize() ?: 0) +
            // Add length size for set value
            setItemLength.calculateVarByteLength() +
            // Add bytes for set value
            setItemLength
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val valueDefinition = (setDefinition.valueDefinition as IsBytesEncodable<T>)

        // Write set index with a SetValue type
        this.parentReference?.propertyDefinition?.index?.writeVarIntWithExtraInfo(SET.value, writer)
        // Write set item bytes
        valueDefinition.calculateStorageByteLength(value).writeVarBytes(writer)
        // Write value bytes
        valueDefinition.writeStorageBytes(value, writer)
    }

    override fun resolve(values: Set<T>): T? {
        return value
    }
}

package maryk.core.properties.references

import maryk.core.exceptions.StorageException
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.index.toReferenceStorageByteArray
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.IsValuesGetter

/**
 * Reference to a simple value by [type] [E] on [parentReference]
 * Can be a reference to a type below a multi type wrapper or for like multi types within lists
 */
class SimpleTypedValueReference<E : TypeEnum<T>, T: Any, in CX : IsPropertyContext> internal constructor(
    val type: E,
    multiTypeDefinition: IsMultiTypeDefinition<E, T, CX>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : CanHaveSimpleChildReference<
        T,
        IsSimpleValueDefinition<T, CX>,
        CanHaveComplexChildReference<*, *, *, *>,
        TypedValue<E, T>
    >(
        multiTypeDefinition.definition(type) as IsSimpleValueDefinition<T, CX>,
        parentReference
    ),
    IsIndexablePropertyReference<T>,
    IsPropertyReferenceWithParent<T, IsSimpleValueDefinition<T, CX>, CanHaveComplexChildReference<*, *, *, *>, TypedValue<E, T>> {

    override val indexKeyPartType = IndexKeyPartType.Reference
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    override val completeName: String by lazy {
        this.parentReference?.let {
            "${it.completeName}.>${type.name}"
        } ?: ">${type.name}"
    }

    override fun resolveFromAny(value: Any) = (value as? TypedValue<*, *>)?.let {
        @Suppress("UNCHECKED_CAST")
        if (it.type == type) it.value as T? else null
    } ?: throw UnexpectedValueException("Expected typed value to get value by reference")

    /** Convenience infix method to create reference [value] pairs */
    @Suppress("UNCHECKED_CAST")
    infix fun with(value: T): ReferenceValuePair<T> =
        ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1 + type.index.calculateVarByteLength()
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(1u, VAR_INT, writer)
        type.index.writeVarBytes(writer)
    }

    override fun calculateSelfStorageByteLength() = 0

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        // The storage bytes are purely for getting the stored value and cannot be converted back to reference
    }

    override fun resolve(values: TypedValue<E, T>): T? = if (values.type == type) values.value else null

    override fun getValue(values: IsValuesGetter): T {
        @Suppress("UNCHECKED_CAST")
        val typedValue = values[parentReference as IsPropertyReference<Any, *, *>]
            ?: throw RequiredException(parentReference)
        return if (typedValue is TypedValue<*, *>) {
            if (typedValue.type == type) {
                @Suppress("UNCHECKED_CAST")
                typedValue.value as T
            } else throw RequiredException(this)
        } else if (typedValue is MultiTypeEnum<*>) {
            throw RequiredException(this)
        } else throw StorageException("Unknown type for $typedValue")
    }

    override fun isForPropertyReference(propertyReference: AnyPropertyReference): Boolean =
        propertyReference == this

    override fun toQualifierStorageByteArray() = parentReference?.toStorageByteArray()

    override fun calculateReferenceStorageByteLength(): Int {
        return this.parentReference?.calculateStorageByteLength() ?: 0
    }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        this.parentReference?.writeStorageBytes(writer)
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel): Boolean =
        dataModel.compatibleWithReference(this)

    override fun readStorageBytes(length: Int, reader: () -> Byte): T =
        comparablePropertyDefinition.readStorageBytes(length, reader)

    override fun calculateStorageByteLength(value: T): Int =
        comparablePropertyDefinition.calculateStorageByteLength(value)

    override fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit) {
        comparablePropertyDefinition.writeStorageBytes(value, writer)
    }
}

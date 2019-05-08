package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.ReferenceType.TYPE
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

@Suppress("UNCHECKED_CAST")
/**
 * Reference to a value by [type] [E] on [parentReference]
 * Can be a reference to a type below a multi type wrapper or for like multi types within lists
 */
class TypedValueReference<E : TypeEnum<T>, T: Any, in CX : IsPropertyContext> internal constructor(
    val type: E,
    multiTypeDefinition: IsMultiTypeDefinition<E, T, CX>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : CanHaveComplexChildReference<Any, IsSubDefinition<Any, CX>,
    CanHaveComplexChildReference<*, *, *, *>,
    TypedValue<E, Any>>(
        multiTypeDefinition.definitionMap[type] as IsSubDefinition<Any, CX>,
        parentReference
    ),
    CanContainListItemReference<Any, IsSubDefinition<Any, CX>, TypedValue<E, Any>>,
    CanContainSetItemReference<Any, IsSubDefinition<Any, CX>, TypedValue<E, Any>>,
    CanContainMapItemReference<Any, IsSubDefinition<Any, CX>, TypedValue<E, Any>>,
    IsPropertyReferenceWithParent<Any, IsSubDefinition<Any, CX>, CanHaveComplexChildReference<*, *, *, *>, TypedValue<E, Any>>,
    HasEmbeddedPropertyReference<Any> {
    override val completeName: String
        get() = this.parentReference?.let {
            "${it.completeName}.*${type.name}"
        } ?: "*${type.name}"

    override fun resolveFromAny(value: Any) = (value as? TypedValue<*, *>)?.value
        ?: throw UnexpectedValueException("Expected typed value to get value by reference")

    override fun getEmbedded(name: String, context: IsPropertyContext?): IsPropertyReference<Any, *, *> {
        return if (this.propertyDefinition is IsEmbeddedDefinition<*, *>) {
            this.propertyDefinition.resolveReferenceByName(name, this)
        } else throw DefNotFoundException("Type reference can not contain embedded name references ($name)")
    }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): AnyPropertyReference {
        if (this.propertyDefinition is IsEmbeddedObjectDefinition<*, *, *, *, *>) {
            return this.propertyDefinition.resolveReference(reader, this)
        } else throw DefNotFoundException("Type reference can not contain embedded index references (${type.name})")
    }

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference {
        return if (this.propertyDefinition is IsEmbeddedObjectDefinition<*, *, *, *, *>) {
            this.propertyDefinition.resolveReferenceFromStorage(reader, this, context, isDoneReading)
        } else throw DefNotFoundException("Type reference can not contain embedded index references (${type.name})")
    }

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1 + type.index.calculateVarByteLength()
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0u, VAR_INT, writer)
        type.index.writeVarBytes(writer)
    }

    override fun calculateSelfStorageByteLength() = type.index.calculateVarIntWithExtraInfoByteSize()

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        // Write type index bytes
        type.index.writeVarIntWithExtraInfo(
            TYPE.value,
            writer
        )
    }

    override fun resolve(values: TypedValue<E, Any>): Any? = values.value
}

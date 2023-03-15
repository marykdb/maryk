package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.pairs.ReferenceValuePair

/** Reference to a List Item on [parentReference] with [T] by [index] */
class ListItemReference<T : Any, CX : IsPropertyContext> internal constructor(
    val index: UInt,
    val listDefinition: IsListDefinition<T, CX>,
    parentReference: CanContainListItemReference<*, *, *>?
) : HasEmbeddedPropertyReference<T>,
    IsPropertyReferenceWithParent<T, IsSubDefinition<T, CX>, CanContainListItemReference<*, *, *>, List<T>>,
    CanHaveComplexChildReference<T, IsSubDefinition<T, CX>, CanContainListItemReference<*, *, *>, List<T>>(
        listDefinition.valueDefinition, parentReference
    ) {
    /** Convenience infix method to create Reference [value] pairs */
    @Suppress("UNCHECKED_CAST")
    infix fun <T : Any> with(value: T) =
        ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)

    override fun getEmbedded(name: String, context: IsPropertyContext?) =
        when (this.propertyDefinition) {
            is IsEmbeddedDefinition<*> ->
                this.propertyDefinition.resolveReferenceByName(name, this)
            is IsMultiTypeDefinition<*, *, *> -> {
                this.propertyDefinition.resolveReferenceByName(name, this)
            }
            else -> throw DefNotFoundException("ListItem can not contain embedded name references ($name)")
        }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): IsPropertyReference<Any, *, *> {
        return when (this.propertyDefinition) {
            is IsEmbeddedDefinition<*> -> {
                this.propertyDefinition.resolveReference(reader, this)
            }
            is IsMultiTypeDefinition<*, *, *> -> {
                this.propertyDefinition.resolveReference(reader, this)
            }
            else -> throw DefNotFoundException("ListItem can not contain embedded index references ($index)")
        }
    }

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference {
        return when (this.propertyDefinition) {
            is IsEmbeddedObjectDefinition<*, *, *, *> -> {
                this.propertyDefinition.resolveReferenceFromStorage(reader, this, context, isDoneReading)
            }
            is IsMultiTypeDefinition<*, *, *> -> {
                this.propertyDefinition.resolveReferenceFromStorage(reader, this)
            }
            else -> throw DefNotFoundException("ListItem can not contain embedded index references ($index)")
        }
    }

    override val completeName: String
        get() = this.parentReference?.let {
            "${it.completeName}.@$index"
        } ?: "@$index"

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1 + index.calculateVarByteLength()
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0u, VAR_INT, writer)
        index.writeVarBytes(writer)
    }

    override fun calculateSelfStorageByteLength() = Int.SIZE_BYTES

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        // Write index bytes
        index.writeBytes(writer)
    }

    override fun resolve(values: List<T>): T? = values.getOrNull(index.toInt())

    override fun resolveFromAny(value: Any) = (value as? List<*>)?.get(this.index.toInt())
        ?: throw UnexpectedValueException("Expected List to get value by reference")
}

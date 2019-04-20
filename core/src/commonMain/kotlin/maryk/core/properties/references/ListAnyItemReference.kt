package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.pairs.ReferenceValuePair

/** Reference to any List Item on [parentReference] with [T] */
class ListAnyItemReference<T : Any, CX : IsPropertyContext> internal constructor(
    listDefinition: IsListDefinition<T, CX>,
    parentReference: ListReference<T, CX>?
) : HasEmbeddedPropertyReference<T>,
    IsFuzzyReference,
    IsPropertyReferenceWithIndirectStorageParent<T, IsValueDefinition<T, CX>, ListReference<T, CX>, List<T>>,
    CanHaveComplexChildReference<T, IsValueDefinition<T, CX>, ListReference<T, CX>, List<T>>(
        listDefinition.valueDefinition, parentReference
    ) {
    /** Convenience infix method to create Reference [value] pairs */
    @Suppress("UNCHECKED_CAST")
    infix fun <T : Any> with(value: T) =
        ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)

    override fun getEmbedded(name: String, context: IsPropertyContext?) =
        when (this.propertyDefinition) {
            is IsEmbeddedDefinition<*, *> ->
                this.propertyDefinition.resolveReferenceByName(name, this)
            is MultiTypeDefinition<*, *> -> {
                this.propertyDefinition.resolveReferenceByName(name, this)
            }
            else -> throw DefNotFoundException("ListItem can not contain embedded name references ($name)")
        }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): IsPropertyReference<Any, *, *> {
        return when (this.propertyDefinition) {
            is IsEmbeddedDefinition<*, *> -> {
                this.propertyDefinition.resolveReference(reader, this)
            }
            is MultiTypeDefinition<*, *> -> {
                this.propertyDefinition.resolveReference(reader, this)
            }
            else -> throw DefNotFoundException("ListItem can not contain embedded index references")
        }
    }

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: CompleteReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference {
        return when (this.propertyDefinition) {
            is IsEmbeddedObjectDefinition<*, *, *, *, *> -> {
                this.propertyDefinition.resolveReferenceFromStorage(reader, this, context, isDoneReading)
            }
            is MultiTypeDefinition<*, *> -> {
                this.propertyDefinition.resolveReferenceFromStorage(reader, this)
            }
            else -> throw DefNotFoundException("ListItem can not contain embedded index references")
        }
    }

    override val completeName: String
        get() = this.parentReference?.let {
            "${it.completeName}.*"
        } ?: "*"

    override fun fuzzyMatcher() = FuzzyExactLengthMatch(4)

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(1u, WireType.VAR_INT, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        return 1 + // The type byte
            // The map index
            (this.parentReference?.propertyDefinition?.index?.calculateVarByteLength() ?: 0) +
            1
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        writer(CompleteReferenceType.LIST_ANY_VALUE.value)
        this.parentReference?.propertyDefinition?.index?.writeVarBytes(writer)
        writer(0)
    }

    override fun resolve(values: List<T>): T? =
        throw RequestException("Cannot get a specific value with any value reference")

    override fun resolveFromAny(value: Any) =
        throw RequestException("Cannot get a specific value with any value reference")
}

package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.pairs.ReferenceValuePair

/** Reference to any List Item on [parentReference] with [T] */
class ListAnyItemReference<T : Any, CX : IsPropertyContext> internal constructor(
    listDefinition: IsListDefinition<T, CX>,
    parentReference: IsPropertyReference<*, *, *>?
) : HasEmbeddedPropertyReference<T>,
    IsFuzzyReference,
    IsPropertyReferenceWithParent<List<T>, IsListDefinition<T, CX>, IsPropertyReference<*, *, *>, List<T>>,
    CanHaveComplexChildReference<List<T>, IsListDefinition<T, CX>, IsPropertyReference<*, *, *>, List<T>>(
        listDefinition, parentReference
    ) {
    /** Convenience infix method to create Reference [value] pairs */
    @Suppress("UNCHECKED_CAST")
    infix fun <T : Any> with(value: T) =
        ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)

    override fun getEmbedded(name: String, context: IsPropertyContext?) =
        when (val valueDefinition = this.propertyDefinition.valueDefinition) {
            is IsEmbeddedDefinition<*, *> ->
                valueDefinition.resolveReferenceByName(name, this)
            is MultiTypeDefinition<*, *, *> -> {
                valueDefinition.resolveReferenceByName(name, this)
            }
            else -> throw DefNotFoundException("ListItem can not contain embedded name references ($name)")
        }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): IsPropertyReference<Any, *, *> {
        return when (val valueDefinition = this.propertyDefinition.valueDefinition) {
            is IsEmbeddedDefinition<*, *> -> {
                valueDefinition.resolveReference(reader, this)
            }
            is MultiTypeDefinition<*, *, *> -> {
                valueDefinition.resolveReference(reader, this)
            }
            else -> throw DefNotFoundException("ListItem can not contain embedded index references")
        }
    }

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference {
        return when (this.propertyDefinition) {
            is IsEmbeddedObjectDefinition<*, *, *, *, *> -> {
                this.propertyDefinition.resolveReferenceFromStorage(reader, this, context, isDoneReading)
            }
            is MultiTypeDefinition<*, *, *> -> {
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
        ProtoBuf.writeKey(1u, VAR_INT, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        throw NotImplementedError("List any item reference is not supported to convert to storage bytes. It uses fuzzy matchers instead")
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        throw NotImplementedError("List any item reference is not supported to convert to storage bytes. It uses fuzzy matchers instead")
    }

    override fun resolve(values: List<T>): List<T> = values

    override fun resolveFromAny(value: Any) = value
}

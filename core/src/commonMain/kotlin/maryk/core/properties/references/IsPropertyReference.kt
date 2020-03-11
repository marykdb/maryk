package maryk.core.properties.references

import maryk.core.processors.datastore.matchers.IsFuzzyMatcher
import maryk.core.processors.datastore.matchers.IsQualifierMatcher
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.processors.datastore.matchers.ReferencedQualifierMatcher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

typealias AnyPropertyReference = IsPropertyReference<*, *, *>
typealias TypedPropertyReference<T> = IsPropertyReference<T, IsPropertyDefinition<T>, *>
typealias AnyOutPropertyReference = TypedPropertyReference<out Any>
typealias AnySpecificWrappedPropertyReference = IsPropertyReference<Any, IsDefinitionWrapper<Any, *, *, *>, *>
typealias AnyValuePropertyReference = IsPropertyReference<*, IsValueDefinitionWrapper<*, *, IsPropertyContext, *>, *>

/**
 * Abstract for reference to a property of type [T] defined by [D] in Values [V]
 */
interface IsPropertyReference<T : Any, out D : IsPropertyDefinition<T>, V : Any> : IsPropertyReferenceForCache<T, D> {
    val completeName: String
    override val propertyDefinition: D

    /** Wrapper could be passed. This makes sure it is the lowest property definition. */
    val comparablePropertyDefinition: D
        get() =
            this.propertyDefinition.let {
                if (it is IsValueDefinitionWrapper<*, *, *, *>) {
                    @Suppress("UNCHECKED_CAST")
                    it.definition as D
                } else {
                    it
                }
            }

    /**
     * Calculate the transport length of encoding this reference
     * and stores result in [cacher] if relevant
     */
    fun calculateTransportByteLength(cacher: WriteCacheWriter): Int

    /**
     * Write transport bytes of property reference to [writer] and gets any needed
     * cached values from [cacheGetter]
     */
    fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit)

    /** Calculate the storage length of encoding only this reference */
    fun calculateSelfStorageByteLength(): Int

    /** Write storage bytes of only property reference to [writer] */
    fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit)

    /** Calculate the storage length of encoding this reference and parents */
    fun calculateStorageByteLength(): Int

    /** Write storage bytes of property reference and parents to [writer] */
    fun writeStorageBytes(writer: (byte: Byte) -> Unit)

    /** Resolve the value from the given [values] */
    fun resolve(values: V): T?

    /** Get [value] with the reference */
    fun resolveFromAny(value: Any): Any

    /** Unwraps the references into an ordered list with parent to children */
    @Suppress("UNCHECKED_CAST")
    fun unwrap(
        listToUse: MutableList<IsPropertyReference<*, *, *>>? = null
    ): List<IsPropertyReference<*, *, in Any>> {
        val refList =
            (listToUse?.also { it.clear(); it.add(this) } ?: mutableListOf(this)) as MutableList<IsPropertyReference<*, *, in Any>>

        var ref = this as IsPropertyReference<*, *, in Any>
        while (ref is IsPropertyReferenceWithParent<*, *, *, *> && ref.parentReference != null) {
            ref = ref.parentReference as IsPropertyReference<*, *, in Any>
            refList.add(0, ref)
        }
        return refList
    }

    /** Convert property reference to a ByteArray */
    fun toStorageByteArray(): ByteArray {
        var index = 0
        val referenceToCompareTo = ByteArray(this.calculateStorageByteLength())
        this.writeStorageBytes { referenceToCompareTo[index++] = it }
        return referenceToCompareTo
    }

    /** Convert property reference to a ByteArray with [prefixBytes] as start */
    fun toStorageByteArray(prefixBytes: ByteArray, offset: Int = 0, length: Int = prefixBytes.size): ByteArray {
        val referenceToCompareTo = ByteArray(length + this.calculateStorageByteLength())
        prefixBytes.copyInto(referenceToCompareTo, 0, offset, offset + length)
        var index = length
        this.writeStorageBytes { referenceToCompareTo[index++] = it }
        return referenceToCompareTo
    }

    fun toQualifierMatcher(childMatcher: ReferencedQualifierMatcher? = null): IsQualifierMatcher {
        val bytes = mutableListOf<Byte>()
        val byteArrays = mutableListOf<ByteArray>()
        val fuzzyMatchers = mutableListOf<IsFuzzyMatcher>()

        var ref: IsPropertyReference<*, *, *> = this
        var lastRef: IsPropertyReference<*, *, *>? = null
        var referenceRef: ObjectReferencePropertyReference<*, *, *, *>? = null

        var writeIndex = 0
        while (ref !== lastRef) {
            if (ref is IsFuzzyReference) {
                if(bytes.isNotEmpty()) {
                    byteArrays.add(0, bytes.toByteArray())
                    bytes.clear()
                }
                fuzzyMatchers.add(0, ref.fuzzyMatcher())
            } else {
                ref.writeSelfStorageBytes {
                    bytes.add(writeIndex++, it)
                }
            }
            writeIndex = 0

            lastRef = ref
            if (ref is IsPropertyReferenceWithParent<*, *, *, *> && ref.parentReference != null) {
                ref.parentReference!!.let {
                    if (it is ObjectReferencePropertyReference<*, *, *, *>) {
                        referenceRef = it
                    } else {
                        ref = it
                    }
                }
            }
        }

        if(bytes.isNotEmpty()) {
            byteArrays.add(0, bytes.toByteArray())
        }

        val resultingMatcher = if (fuzzyMatchers.isEmpty()) {
            QualifierExactMatcher(
                qualifier = bytes.toByteArray(),
                referencedQualifierMatcher = childMatcher
            )
        } else {
            QualifierFuzzyMatcher(
                qualifierParts = byteArrays,
                fuzzyMatchers = fuzzyMatchers,
                referencedQualifierMatcher = childMatcher
            )
        }

        // If it encounters reference, wrap. Otherwise, return resultingMatcher.
        return referenceRef?.toQualifierMatcher(
            ReferencedQualifierMatcher(referenceRef!!, resultingMatcher)
        ) ?: resultingMatcher
    }
}

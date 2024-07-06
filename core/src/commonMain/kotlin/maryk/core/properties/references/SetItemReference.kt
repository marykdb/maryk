package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.pairs.ReferenceNullPair
import maryk.core.query.pairs.ReferenceValuePair
import kotlin.js.JsName

/**
 * Reference to a Set Item by [value] of [T] and context [CX] on set referred to [parentReference] and
 * defined by [setDefinition]
 */
class SetItemReference<T : Any, CX : IsPropertyContext> internal constructor(
    val value: T,
    val setDefinition: IsSetDefinition<T, CX>,
    parentReference: CanContainSetItemReference<*, *, *>?
) : CanHaveSimpleChildReference<T, IsValueDefinition<T, CX>, CanContainSetItemReference<*, *, *>, Set<T>>(
        setDefinition.valueDefinition, parentReference
    ),
    IsPropertyReferenceWithParent<T, IsValueDefinition<T, CX>, CanContainSetItemReference<*, *, *>, Set<T>> {
    override val completeName: String by lazy {
        this.parentReference?.let {
            "${it.completeName}.#$value"
        } ?: "#$value"
    }

    @Suppress("UNCHECKED_CAST")
    infix fun <T : Any> with(value: T) =
        ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)

    @JsName("withValueOrNull")
    infix fun <T : Any> with(value: T?) =
        @Suppress("UNCHECKED_CAST")
        if (value == null) {
            ReferenceNullPair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>)
        } else {
            ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)
        }

    override fun resolveFromAny(value: Any) =
        if (value is Set<*> && value.contains(this.value)) {
            this.value
        } else {
            throw UnexpectedValueException("Expected List to get value by reference")
        }

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        val valueLength = setDefinition.valueDefinition.calculateTransportByteLengthWithKey(0, value, cacher)
        return parentLength + valueLength
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        setDefinition.valueDefinition.writeTransportBytesWithKey(0, value, cacheGetter, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        @Suppress("UNCHECKED_CAST")
        val setItemLength = (this.setDefinition.valueDefinition as IsStorageBytesEncodable<T>).calculateStorageByteLength(this.value)

        // Add length size for set value
        return setItemLength.calculateVarByteLength() +
            // Add bytes for set value
            setItemLength
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val valueDefinition = (setDefinition.valueDefinition as IsStorageBytesEncodable<T>)

        // Write set item bytes
        valueDefinition.calculateStorageByteLength(value).writeVarBytes(writer)
        // Write value bytes
        valueDefinition.writeStorageBytes(value, writer)
    }

    override fun resolve(values: Set<T>) =
        values.find { this.value == it }
}

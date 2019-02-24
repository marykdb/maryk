package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.exceptions.RequiredException
import maryk.core.values.IsValuesGetter

/**
 * Reference to a value property containing values of type [T] which are of fixed byte length. This can be used inside
 * keys. The property is defined by Property Definition Wrapper [propertyDefinition] of type [D]
 * and referred by PropertyReference of type [P].
 */
open class ValueWithFixedBytesPropertyReference<
    T : Any,
    TO : Any,
    out D : FixedBytesPropertyDefinitionWrapper<T, TO, *, *, *>,
    out P : AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
) :
    PropertyReferenceForValues<T, TO, D, P>(propertyDefinition, parentReference),
    IsPropertyReferenceForValues<T, TO, D, P>,
    IsValuePropertyReference<T, TO, D, P>,
    IsFixedBytesPropertyReference<T>,
    IsFixedBytesEncodable<T> by propertyDefinition {
    override val byteSize = propertyDefinition.byteSize
    override val indexKeyPartType = IndexKeyPartType.Reference

    override fun calculateStorageByteLength(value: T) = this.byteSize

    override fun calculateReferenceStorageByteLength(): Int {
        val refLength = this.calculateStorageByteLength()
        return refLength.calculateVarIntWithExtraInfoByteSize() + refLength
    }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        val refLength = this.calculateStorageByteLength()
        refLength.writeVarIntWithExtraInfo(
            this.indexKeyPartType.index.toByte(),
            writer
        )
        this.writeStorageBytes(writer)
    }

    override fun getValue(values: IsValuesGetter) =
        values[this] ?: throw RequiredException(this)

    override fun isForPropertyReference(propertyReference: IsPropertyReference<*, *, *>) =
        propertyReference == this
}

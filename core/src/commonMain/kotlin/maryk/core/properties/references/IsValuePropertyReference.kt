package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.definitions.IsBytesEncodable
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.RequiredException
import maryk.core.values.IsValuesGetter

/**
 * Reference to a value property containing values of type [T]. The property is defined by Property Definition Wrapper
 * [D] and referred by PropertyReference of type [P].
 */
interface IsValuePropertyReference<
    T : Any,
    TO : Any,
    out D,
    out P : AnyPropertyReference
> :
    IsPropertyReferenceForValues<T, TO, D, P>,
    IsIndexablePropertyReference<T>,
    IsBytesEncodable<T>
        where D : IsBytesEncodable<T>, D : IsPropertyDefinitionWrapper<T, TO, *, *> {
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

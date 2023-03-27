package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsRootModel
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.index.toReferenceStorageByteArray
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.types.Bytes
import maryk.core.values.IsValuesGetter

/**
 * Reference to a value property containing values of type [T] which are of fixed byte length. This can be used inside
 * keys. The property is defined by Property Definition Wrapper [propertyDefinition] of type [D]
 * and referred by PropertyReference of type [P].
 */
open class ValueWithFixedBytesPropertyReference<
    T : Any,
    TO : Any,
    out D : FixedBytesDefinitionWrapper<T, TO, *, *, *>,
    out P : AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
) :
    PropertyReferenceForValues<T, TO, D, P>(propertyDefinition, parentReference),
    IsPropertyReferenceForValues<T, TO, D, P>,
    IsValuePropertyReference<T, TO, D, P>,
    IsFixedBytesPropertyReference<T>,
    IsFixedStorageBytesEncodable<T> by propertyDefinition {
    override val byteSize = propertyDefinition.byteSize
    override val indexKeyPartType = IndexKeyPartType.Reference
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

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

    override fun isCompatibleWithModel(dataModel: IsRootModel) =
        dataModel.compatibleWithReference(this)
}

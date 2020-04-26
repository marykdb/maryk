package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.index.toReferenceStorageByteArray
import maryk.core.properties.definitions.wrapper.ReferenceDefinitionWrapper
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.values.AbstractValues
import maryk.core.values.IsValuesGetter

/**
 * Reference to a value property containing keys for data model of [DM].
 * The property is defined by Property Definition Wrapper [propertyDefinition] of type [D]
 * and referred by parent PropertyReference of type [P].
 */
open class ObjectReferencePropertyReference<
    DM: IsRootDataModel<*>,
    TO : Any,
    out D : ReferenceDefinitionWrapper<TO, DM, *, *, *>,
    out P : AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
) :
    CanHaveComplexChildReference<Key<DM>, D, P, AbstractValues<*, *, *>>(propertyDefinition, parentReference),
    IsPropertyReferenceForValues<Key<DM>, TO, D, P>,
    IsValuePropertyReference<Key<DM>, TO, D, P>,
    IsFixedBytesPropertyReference<Key<DM>>,
    IsFixedStorageBytesEncodable<Key<DM>> by propertyDefinition {
    override val name = propertyDefinition.name
    override val byteSize = propertyDefinition.byteSize
    override val indexKeyPartType = IndexKeyPartType.Reference
    override val referenceStorageByteArray = Bytes(this.toReferenceStorageByteArray())

    override fun calculateStorageByteLength(value: Key<DM>) = this.byteSize

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

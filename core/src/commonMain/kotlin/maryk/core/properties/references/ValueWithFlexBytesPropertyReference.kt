package maryk.core.properties.references

import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.index.toReferenceStorageByteArray
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.types.Bytes

/**
 * Reference to a value property containing values of type [T]. The property is defined by Property Definition Wrapper
 * [D] and referred by PropertyReference of type [P].
 */
open class ValueWithFlexBytesPropertyReference<
    T : Any,
    TO : Any,
    out D : FlexBytesDefinitionWrapper<T, TO, *, *, *>,
    out P : AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
) :
    PropertyReferenceForValues<T, TO, D, P>(propertyDefinition, parentReference),
    IsValuePropertyReference<T, TO, D, P>,
    IsStorageBytesEncodable<T> by propertyDefinition {
    override val indexKeyPartType = IndexKeyPartType.Reference
    override val referenceStorageByteArray = Bytes(this.toReferenceStorageByteArray())
}

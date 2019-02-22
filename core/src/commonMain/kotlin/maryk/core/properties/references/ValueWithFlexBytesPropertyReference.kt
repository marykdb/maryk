package maryk.core.properties.references

import maryk.core.properties.definitions.IsBytesEncodable
import maryk.core.properties.definitions.key.IndexKeyPartType

/**
 * Reference to a value property containing values of type [T]. The property is defined by Property Definition Wrapper
 * [D] and referred by PropertyReference of type [P].
 */
open class ValueWithFlexBytesPropertyReference<
    T: Any,
    TO: Any,
    out D : FlexBytesPropertyDefinitionWrapper<T, TO, *, *, *>,
    out P: AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
):
    PropertyReferenceForValues<T, TO, D, P>(propertyDefinition, parentReference),
    IsValuePropertyReference<T, TO, D, P>,
    IsBytesEncodable<T> by propertyDefinition
{
    override val indexKeyPartType = IndexKeyPartType.Reference
}

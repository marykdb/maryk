package maryk.core.properties.references

import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.values.AbstractValues

/**
 * Reference to a property containing values of type [T] inside Values. The property is defined by Property Definition Wrapper
 * [D] and referred by PropertyReference of type [P].
 */
open class PropertyReferenceForValues<
    T : Any,
    TO : Any,
    out D : IsPropertyDefinitionWrapper<T, TO, *, *>,
    out P : AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
) : PropertyReference<T, D, P, AbstractValues<*, *, *>>(propertyDefinition, parentReference),
    IsPropertyReferenceForValues<T, TO, D, P> {
    override val name = this.propertyDefinition.name
}

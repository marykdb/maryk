package maryk.core.properties.references

import maryk.core.values.AbstractValues
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper

/**
 * Reference to a value property containing values of type [T]. The property is defined by Property Definition Wrapper
 * [D] and referred by PropertyReference of type [P].
 */
open class ValuePropertyReference<
    T: Any,
    TO: Any,
    out D : IsPropertyDefinitionWrapper<T, TO, *, *>,
    out P: AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
): PropertyReference<T, D, P, AbstractValues<*, *, *>>(propertyDefinition, parentReference), IsValuePropertyReference<T, TO, D, P> {
    override val name = this.propertyDefinition.name
}

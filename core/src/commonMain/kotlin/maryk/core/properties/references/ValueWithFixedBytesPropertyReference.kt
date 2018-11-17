package maryk.core.properties.references

import maryk.core.objects.AbstractValues
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper

/**
 * Reference to a value property containing values of type [T] which are of fixed byte length. This can be used inside
 * keys. The property is defined by Property Definition Wrapper [propertyDefinition] of type [D]
 * and referred by PropertyReference of type [P].
 */
open class ValueWithFixedBytesPropertyReference<
    T: Any,
    TO: Any,
    out D : FixedBytesPropertyDefinitionWrapper<T, TO, *, *, *>,
    out P: AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
): PropertyReference<T, D, P, AbstractValues<*, *, *>>(propertyDefinition, parentReference), IsValuePropertyReference<T, TO, D, P> {
    override val name = this.propertyDefinition.name
}

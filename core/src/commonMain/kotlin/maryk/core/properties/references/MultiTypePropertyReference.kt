package maryk.core.properties.references

import maryk.core.objects.AbstractValues
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.types.TypedValue

/**
 * Reference to a value property containing multi type values of types [E].
 * The property is defined by Multi type Property Definition Wrapper
 * [D] and referred by PropertyReference of type [P].
 */
open class MultiTypePropertyReference<
    E: IndexedEnum<E>,
    TO: Any,
    out D : MultiTypeDefinitionWrapper<E, TO, *, *>,
    out P: AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
): CanHaveComplexChildReference<TypedValue<E, Any>, D, P, AbstractValues<*, *, *>>(
    propertyDefinition,
    parentReference
), IsValuePropertyReference<TypedValue<E, Any>, TO, D, P> {
    override val name = this.propertyDefinition.name
}

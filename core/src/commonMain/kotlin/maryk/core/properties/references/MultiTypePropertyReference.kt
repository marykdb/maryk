package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.values.AbstractValues

/**
 * Reference to a value property containing multi type values of types [E].
 * The property is defined by Multi type Property Definition Wrapper
 * [D] and referred by PropertyReference of type [P].
 */
open class MultiTypePropertyReference<
    E : TypeEnum<T>,
    T: Any,
    TO : Any,
    out D : MultiTypeDefinitionWrapper<E, T, TO, *, *>,
    out P : AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
) : CanHaveComplexChildReference<TypedValue<E, T>, D, P, AbstractValues<*, *>>(
    propertyDefinition,
    parentReference
), IsPropertyReferenceForValues<TypedValue<E, T>, TO, D, P>, HasEmbeddedPropertyReference<TypedValue<E, T>> {
    override val name = this.propertyDefinition.name
    override val completeName by lazy(this::generateCompleteName)

    override fun getEmbedded(name: String, context: IsPropertyContext?) =
        this.propertyDefinition.resolveReferenceByName(name, this)

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?) =
        this.propertyDefinition.definition.resolveReference(reader, this)

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean
    ) = propertyDefinition.definition.resolveReferenceFromStorage(reader, this)
}

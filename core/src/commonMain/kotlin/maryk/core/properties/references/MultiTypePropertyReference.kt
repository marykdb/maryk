package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.types.TypedValue
import maryk.core.values.AbstractValues

/**
 * Reference to a value property containing multi type values of types [E].
 * The property is defined by Multi type Property Definition Wrapper
 * [D] and referred by PropertyReference of type [P].
 */
open class MultiTypePropertyReference<
    E : IndexedEnum,
    TO : Any,
    out D : MultiTypeDefinitionWrapper<E, TO, *, *>,
    out P : AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
) : CanHaveComplexChildReference<TypedValue<E, Any>, D, P, AbstractValues<*, *, *>>(
    propertyDefinition,
    parentReference
), IsPropertyReferenceForValues<TypedValue<E, Any>, TO, D, P>, HasEmbeddedPropertyReference<TypedValue<E, Any>> {
    override val name = this.propertyDefinition.name

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

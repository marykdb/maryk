package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.AbstractObjectDataModel
import maryk.core.values.AbstractValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.EmbeddedObjectPropertyDefinitionWrapper

/**
 * Reference to a Embed property containing type [DO] DataObjects, [P] ObjectPropertyDefinitions. Which is defined by
 * DataModel of type [DM] and expects context of type [CX] which is transformed into context [CXI] for properties.
 */
class EmbeddedObjectPropertyRef<
    DO : Any,
    TO: Any,
    P: ObjectPropertyDefinitions<DO>,
    out DM : AbstractObjectDataModel<DO, P, CXI, CX>,
    CXI: IsPropertyContext,
    CX: IsPropertyContext
> internal constructor(
    propertyDefinition: EmbeddedObjectPropertyDefinitionWrapper<DO, TO, P, DM, CXI, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
): CanHaveComplexChildReference<DO, EmbeddedObjectPropertyDefinitionWrapper<DO, TO, P, DM, CXI, CX, *>, CanHaveComplexChildReference<*, *, *, *>, AbstractValues<*, *, *>>(
    propertyDefinition, parentReference
), HasEmbeddedPropertyReference<DO>, IsValuePropertyReference<DO, TO, EmbeddedObjectPropertyDefinitionWrapper<DO, TO, P, DM, CXI, CX, *>, CanHaveComplexChildReference<*, *, *, *>> {
    override val name = this.propertyDefinition.name

    override fun getEmbedded(name: String, context: IsPropertyContext?) =
        this.propertyDefinition.definition.dataModel.properties[name]?.getRef(this)
            ?: throw DefNotFoundException("Embedded Definition with $name not found")

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): AnyPropertyReference {
        val index = initIntByVar(reader)
        return this.propertyDefinition.definition.dataModel.properties[index]?.getRef(this)
                ?: throw DefNotFoundException("Embedded Definition with $name not found")
    }
}

package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.EmbeddedObjectPropertyDefinitionWrapper
import maryk.core.properties.references.ReferenceType.EMBED
import maryk.core.values.AbstractValues

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
        this.propertyDefinition.definition.dataModel.properties[name]?.ref(this)
            ?: throw DefNotFoundException("Embedded Definition with $name not found")

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?) =
        this.propertyDefinition.resolveReference(reader, parentReference)

    override fun getEmbeddedStorageRef(reader: () -> Byte, context: IsPropertyContext?, referenceType: CompleteReferenceType, isDoneReading: () -> Boolean): AnyPropertyReference =
        this.propertyDefinition.resolveReferenceFromStorage(reader, parentReference, context, isDoneReading)

    override fun writeStorageBytes(writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeStorageBytes(writer)
        this.propertyDefinition.index.writeVarIntWithExtraInfo(EMBED.value, writer)
    }
}

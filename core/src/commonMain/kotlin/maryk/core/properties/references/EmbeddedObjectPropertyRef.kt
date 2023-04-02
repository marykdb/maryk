package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsSimpleBaseObjectDataModel
import maryk.core.properties.definitions.wrapper.EmbeddedObjectDefinitionWrapper
import maryk.core.properties.references.ReferenceType.EMBED
import maryk.core.values.AbstractValues

/**
 * Reference to a Embed property containing type [DO] DataObjects. Which is defined by
 * DataModel of type [DM] and expects context of type [CX] which is transformed into context [CXI] for properties.
 */
class EmbeddedObjectPropertyRef<
    DO : Any,
    TO : Any,
    DM : IsSimpleBaseObjectDataModel<DO, CXI, CX>,
    CXI : IsPropertyContext,
    CX : IsPropertyContext
> internal constructor(
    propertyDefinition: EmbeddedObjectDefinitionWrapper<DO, TO, DM, CXI, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : CanHaveComplexChildReference<DO, EmbeddedObjectDefinitionWrapper<DO, TO, DM, CXI, CX, *>, CanHaveComplexChildReference<*, *, *, *>, AbstractValues<*, *>>(
    propertyDefinition, parentReference
), HasEmbeddedPropertyReference<DO>,
    IsPropertyReferenceForValues<DO, TO, EmbeddedObjectDefinitionWrapper<DO, TO, DM, CXI, CX, *>, CanHaveComplexChildReference<*, *, *, *>> {
    override val name = this.propertyDefinition.name

    override fun getEmbedded(name: String, context: IsPropertyContext?) =
        this.propertyDefinition.definition.dataModel[name]?.ref(this)
            ?: throw DefNotFoundException("Embedded Definition with $name not found")

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?) =
        this.propertyDefinition.resolveReference(reader, parentReference)

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference =
        this.propertyDefinition.resolveReferenceFromStorage(reader, parentReference, context, isDoneReading)

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        this.propertyDefinition.index.writeVarIntWithExtraInfo(EMBED.value, writer)
    }
}

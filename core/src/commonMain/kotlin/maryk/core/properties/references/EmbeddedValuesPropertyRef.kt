package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.properties.definitions.wrapper.EmbeddedValuesDefinitionWrapper
import maryk.core.properties.references.ReferenceType.EMBED
import maryk.core.query.ContainsDataModelContext
import maryk.core.values.AbstractValues
import maryk.core.values.Values

/**
 * Reference to an Embed property containing type Values, [DM] PropertyDefinitions. Which is defined by
 * DataModel of type [DM] and expects context of type [CX].
 */
class EmbeddedValuesPropertyRef<
    DM : IsValuesPropertyDefinitions,
    CX : IsPropertyContext
> internal constructor(
    propertyDefinition: EmbeddedValuesDefinitionWrapper<DM, CX>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : CanHaveComplexChildReference<Values<DM>, EmbeddedValuesDefinitionWrapper<DM, CX>, CanHaveComplexChildReference<*, *, *, *>, AbstractValues<*, *>>(
    propertyDefinition, parentReference
), HasEmbeddedPropertyReference<Values<DM>>,
    IsPropertyReferenceForValues<Values<DM>, Values<DM>, EmbeddedValuesDefinitionWrapper<DM, CX>, CanHaveComplexChildReference<*, *, *, *>> {
    override val name = this.propertyDefinition.name

    override fun getEmbedded(name: String, context: IsPropertyContext?) =
        if (this.propertyDefinition.definition is ContextualEmbeddedValuesDefinition<*> && context is ContainsDataModelContext<*>) {
            context.dataModel?.get(name)?.ref(this)
                ?: throw DefNotFoundException("Embedded Definition with $name not found")
        } else {
            this.propertyDefinition.definition.dataModel[name]?.ref(this)
                ?: throw DefNotFoundException("Embedded Definition with $name not found")
        }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): AnyPropertyReference {
        val index = initUIntByVar(reader)
        return if (this.propertyDefinition.definition is ContextualEmbeddedValuesDefinition<*> && context is ContainsDataModelContext<*>) {
            context.dataModel?.get(index)?.ref(this)
        } else {
            this.propertyDefinition.definition.dataModel[index]?.ref(this)
        } ?: throw DefNotFoundException("Embedded Definition with $index not found")
    }

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference {
        return decodeStorageIndex(reader) { index, type ->
            val propertyReference =
                if (this.propertyDefinition.definition is ContextualEmbeddedValuesDefinition<*> && context is ContainsDataModelContext<*>) {
                    context.dataModel?.get(index)?.ref(this)
                } else {
                    this.propertyDefinition.definition.dataModel[index]?.ref(this)
                } ?: throw DefNotFoundException("Embedded Definition with $name not found")

            if (isDoneReading()) {
                propertyReference
            } else {
                when (propertyReference) {
                    is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbeddedStorageRef(
                        reader,
                        context,
                        type,
                        isDoneReading
                    )
                    else -> throw DefNotFoundException("More property references found on property that cannot have any: $propertyReference")
                }
            }
        }
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        this.propertyDefinition.index.writeVarIntWithExtraInfo(EMBED.value, writer)
    }
}

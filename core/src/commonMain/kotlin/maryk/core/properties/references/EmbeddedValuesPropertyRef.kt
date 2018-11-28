package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.properties.definitions.wrapper.EmbeddedValuesPropertyDefinitionWrapper
import maryk.core.query.ContainsDataModelContext
import maryk.core.values.AbstractValues
import maryk.core.values.Values

/**
 * Reference to a Embed property containing type Values, [P] PropertyDefinitions. Which is defined by
 * DataModel of type [DM] and expects context of type [CX].
 */
class EmbeddedValuesPropertyRef<
    DM : IsValuesDataModel<P>,
    P: PropertyDefinitions,
    CX: IsPropertyContext,
    PDM: IsValuesDataModel<P>
> internal constructor(
    propertyDefinition: EmbeddedValuesPropertyDefinitionWrapper<DM, P, CX, PDM>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
): CanHaveComplexChildReference<Values<DM, P>, EmbeddedValuesPropertyDefinitionWrapper<DM, P, CX, PDM>, CanHaveComplexChildReference<*, *, *, *>, AbstractValues<*, *, *>>(
    propertyDefinition, parentReference
), HasEmbeddedPropertyReference<Values<DM, P>>, IsValuePropertyReference<Values<DM, P>, Values<DM, P>, EmbeddedValuesPropertyDefinitionWrapper<DM, P, CX, PDM>, CanHaveComplexChildReference<*, *, *, *>> {
    override val name = this.propertyDefinition.name

    override fun getEmbedded(name: String, context: IsPropertyContext?) =
        if (this.propertyDefinition.definition is ContextualEmbeddedValuesDefinition<*> && context is ContainsDataModelContext<*>) {
            (context.dataModel as? IsValuesDataModel<*>)?.properties?.get(name)?.getRef(this)
                    ?: throw DefNotFoundException("Embedded Definition with $name not found")
        } else {
            this.propertyDefinition.definition.dataModel.properties[name]?.getRef(this)
                    ?: throw DefNotFoundException("Embedded Definition with $name not found")
        }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): AnyPropertyReference {
        val index = initIntByVar(reader)
        return if (this.propertyDefinition.definition is ContextualEmbeddedValuesDefinition<*> && context is ContainsDataModelContext<*>) {
            (context.dataModel as? IsValuesDataModel<*>)?.properties?.get(index)?.getRef(this)
        } else {
            this.propertyDefinition.definition.dataModel.properties[index]?.getRef(this)
        } ?: throw DefNotFoundException("Embedded Definition with $index not found")
    }

    override fun getEmbeddedStorageRef(reader: () -> Byte, context: IsPropertyContext?, referenceType: CompleteReferenceType, isDoneReading: () -> Boolean): AnyPropertyReference {
        return decodeStorageIndex(reader) { index, type ->
            val propertyReference = if (this.propertyDefinition.definition is ContextualEmbeddedValuesDefinition<*> && context is ContainsDataModelContext<*>) {
                (context.dataModel as? IsValuesDataModel<*>)?.properties?.get(index)?.getRef(this)
            } else {
                this.propertyDefinition.definition.dataModel.properties[index]?.getRef(this)
            } ?: throw DefNotFoundException("Embedded Definition with $name not found")

            if (isDoneReading()) {
                propertyReference
            } else {
                when (propertyReference) {
                    is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbeddedStorageRef(reader, context, type, isDoneReading)
                    else -> throw DefNotFoundException("More property references found on property that cannot have any: $propertyReference")
                }
            }
        }
    }
}

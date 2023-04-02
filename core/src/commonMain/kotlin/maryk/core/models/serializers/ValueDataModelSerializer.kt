package maryk.core.models.serializers

import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues

/**
 * Serializer for [ValueDataObject]s
 */
open class ValueDataModelSerializer<DO: ValueDataObject, DM: IsObjectPropertyDefinitions<DO>>(
    model: DM,
): ObjectDataModelSerializer<DO, DM, IsPropertyContext, IsPropertyContext>(model) {
    override fun getValueWithDefinition(
        definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        obj: DO,
        context: IsPropertyContext?
    ) = if (obj is ValueDataObjectWithValues) {
        obj.values(definition.index)
    } else {
        super.getValueWithDefinition(definition, obj, context)
    }
}

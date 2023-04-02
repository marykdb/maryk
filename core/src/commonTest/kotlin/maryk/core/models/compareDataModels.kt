package maryk.core.models

import maryk.core.models.definitions.BaseDataModelDefinition
import maryk.core.models.definitions.IsObjectDataModelDefinition
import maryk.core.models.definitions.IsRootDataModelDefinition
import maryk.core.properties.definitions.wrapper.comparePropertyDefinitionWrapper
import kotlin.test.assertEquals

/**
 * Compares if two DataModels are equal
 */
internal fun <DM : BaseDataModelDefinition<*>> compareDataModels(converted: DM, original: DM) {
    (converted.properties)
        .zip(original.properties)
        .forEach { (convertedWrapper, originalWrapper) ->
            comparePropertyDefinitionWrapper(convertedWrapper, originalWrapper)
        }

    if (original is IsObjectDataModelDefinition<*, *>) {
        if (converted !is IsObjectDataModelDefinition<*, *>) {
            throw AssertionError("Converted model should be a ObjectDataModel")
        }
        assertEquals(original.name, converted.name)
    }

    if (original is IsRootDataModelDefinition<*>) {
        if (converted !is IsRootDataModelDefinition<*>) {
            throw AssertionError("Converted model should be a RootObjectDataModel")
        }

        assertEquals(original.keyDefinition, converted.keyDefinition)

        assertEquals(original.indices?.size, converted.indices?.size)
        assertEquals(original.indices, converted.indices)
    }
}

package maryk.core.models

import maryk.core.properties.definitions.wrapper.comparePropertyDefinitionWrapper
import kotlin.test.assertEquals

/**
 * Compares if two DataModels are equal
 */
internal fun <DM : IsStorableDataModel<*>> compareDataModels(converted: DM, original: DM) {
    (converted)
        .zip(original)
        .forEach { (convertedWrapper, originalWrapper) ->
            comparePropertyDefinitionWrapper(convertedWrapper, originalWrapper)
        }

    if (original is IsObjectDataModel<*>) {
        if (converted !is IsObjectDataModel<*>) {
            throw AssertionError("Converted model should be a ObjectDataModel")
        }
    }
    assertEquals(original.Meta.name, converted.Meta.name)

    if (original is IsRootDataModel) {
        if (converted !is IsRootDataModel) {
            throw AssertionError("Converted model should be a RootObjectDataModel")
        }

        assertEquals(original.Meta.keyDefinition, converted.Meta.keyDefinition)

        assertEquals(original.Meta.indexes?.size, converted.Meta.indexes?.size)
        assertEquals(original.Meta.indexes, converted.Meta.indexes)
    }
}

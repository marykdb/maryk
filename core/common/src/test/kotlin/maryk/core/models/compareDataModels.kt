package maryk.core.models

import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.comparePropertyDefinitionWrapper
import maryk.test.shouldBe

/**
 * Compares if two DataModels are equal
 */
internal fun <DM: AbstractDataModel<*, *, *, *>> compareDataModels(converted: DM, original: DM) {
    (converted.properties)
        .zip(original.properties)
        .forEach { (convertedWrapper, originalWrapper) ->
            comparePropertyDefinitionWrapper(convertedWrapper, originalWrapper)
        }

    if (original is ObjectDataModel<*, *>) {
        if(converted !is ObjectDataModel<*, *>) {
            throw AssertionError("Converted model should be a ObjectDataModel")
        }
        converted.name shouldBe original.name
    }

    if (original is RootObjectDataModel<*, *, *>) {
        if(converted !is RootObjectDataModel<*, *, *>) {
            throw AssertionError("Converted model should be a RootObjectDataModel")
        }

        converted.keyDefinitions.zip(original.keyDefinitions).forEach { (converted, original) ->
            when(converted) {
                is IsPropertyDefinitionWrapper<*, *, *, *> -> {
                    comparePropertyDefinitionWrapper(converted, original as IsPropertyDefinitionWrapper<*, *, *, *>)
                }
                else -> converted shouldBe original
            }
        }
    }
}

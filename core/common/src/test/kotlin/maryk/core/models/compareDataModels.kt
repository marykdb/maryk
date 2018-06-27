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

    if (original is DataModel<*, *>) {
        if(converted !is DataModel<*, *>) {
            throw AssertionError("Converted model should be a DataModel")
        }
        converted.name shouldBe original.name
    }

    if (original is RootDataModel<*, *>) {
        if(converted !is RootDataModel<*, *>) {
            throw AssertionError("Converted model should be a RootDataModel")
        }

        converted.key.keyDefinitions.zip(original.key.keyDefinitions).forEach { (converted, original) ->
            when(converted) {
                is IsPropertyDefinitionWrapper<*, *, *, *> -> {
                    comparePropertyDefinitionWrapper(converted, original as IsPropertyDefinitionWrapper<*, *, *, *>)
                }
                else -> converted shouldBe original
            }
        }
    }
}

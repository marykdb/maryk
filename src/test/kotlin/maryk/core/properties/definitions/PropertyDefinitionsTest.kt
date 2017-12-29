package maryk.core.properties.definitions

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.wrapper.comparePropertyDefinitionWrapper
import maryk.core.query.DataModelContext
import kotlin.test.Test

class PropertyDefinitionsTest {
    @Test
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(TestMarykObject.properties, PropertyDefinitions.Model, DataModelContext(), ::compareDataModels)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(TestMarykObject.properties, PropertyDefinitions.Model, DataModelContext(), ::compareDataModels)
    }

    private fun compareDataModels(converted: PropertyDefinitions<*>, original: PropertyDefinitions<*>) {
        @Suppress("UNCHECKED_CAST")
        (converted as PropertyDefinitions<Any>)
                .zip(original as PropertyDefinitions<Any>)
                .forEach { (convertedWrapper, originalWrapper) ->
                    comparePropertyDefinitionWrapper(convertedWrapper, originalWrapper)
                }
    }
}
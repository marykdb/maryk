package maryk.core.objects

import maryk.TestValueObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.comparePropertyDefinitionWrapper
import maryk.core.query.DataModelContext
import maryk.test.shouldBe
import kotlin.test.Test

internal class ValueDataModelTest {
    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(TestValueObject, ValueDataModel.Model, DataModelContext(), ::compareDataModels)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(TestValueObject, ValueDataModel.Model, DataModelContext(), ::compareDataModels)
    }

    private fun compareDataModels(converted: DataModel<out Any, PropertyDefinitions<out Any>>, original: DataModel<out Any, PropertyDefinitions<out Any>>) {
        converted.name shouldBe original.name

        (converted.properties)
                .zip(original.properties)
                .forEach { (convertedWrapper, originalWrapper) ->
                    comparePropertyDefinitionWrapper(convertedWrapper, originalWrapper)
                }
    }
}


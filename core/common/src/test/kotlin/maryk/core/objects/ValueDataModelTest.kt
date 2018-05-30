package maryk.core.objects

import maryk.TestValueObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelContext
import maryk.test.shouldBe
import kotlin.test.Test

internal class ValueDataModelTest {
    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(TestValueObject, ValueDataModel.Model, { DataModelContext() }, ::compareDataModels)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(TestValueObject, ValueDataModel.Model, { DataModelContext() }, ::compareDataModels)
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(TestValueObject, ValueDataModel.Model, { DataModelContext() }, ::compareDataModels) shouldBe  """
        name: TestValueObject
        properties:
          ? 0: int
          : !Number
            indexed: false
            searchable: true
            required: true
            final: false
            unique: false
            type: SInt32
            maxValue: 6
            random: false
          ? 1: dateTime
          : !DateTime
            indexed: false
            searchable: true
            required: true
            final: false
            unique: false
            precision: SECONDS
            fillWithNow: false
          ? 2: bool
          : !Boolean
            indexed: false
            searchable: true
            required: true
            final: false

        """.trimIndent()
    }
}


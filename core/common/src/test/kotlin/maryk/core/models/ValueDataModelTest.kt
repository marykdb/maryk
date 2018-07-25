package maryk.core.models

import maryk.TestValueObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.ValueDataObject
import maryk.core.protobuf.WriteCache
import maryk.core.query.DataModelContext
import maryk.lib.time.DateTime
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class ValueDataModelTest {
    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(TestValueObject, ValueDataModel.Model, { DataModelContext() }, { converted: ValueDataModel<*, *>, original: ValueDataModel<*, *> ->
            compareDataModels(converted, original)

            // Also test conversion with the generated ValueObject

            @Suppress("UNCHECKED_CAST")
            val convertedValueModel = converted as ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>

            val value = converted.map {
                mapOf(
                   convertedValueModel.properties[1]!! withNotNull 5,
                   convertedValueModel.properties[2]!! withNotNull DateTime(2018, 7, 18, 12, 0, 0),
                   convertedValueModel.properties[3]!! withNotNull true
                )
            }.toDataObject()

            val context = DataModelContext()

            val bc = ByteCollector()
            val cache = WriteCache()

            val byteLength = convertedValueModel.calculateProtoBufLength(value, cache, context)
            bc.reserve(byteLength)
            convertedValueModel.writeProtoBuf(value, cache, bc::write, context)
            val convertedValue = convertedValueModel.readProtoBuf(byteLength, bc::read, context).toDataObject()

            convertedValue shouldBe value
        })
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
          ? 1: int
          : !Number
            indexed: false
            required: true
            final: false
            unique: false
            type: SInt32
            maxValue: 6
            random: false
          ? 2: dateTime
          : !DateTime
            indexed: false
            required: true
            final: false
            unique: false
            precision: SECONDS
            fillWithNow: false
          ? 3: bool
          : !Boolean
            indexed: false
            required: true
            final: false

        """.trimIndent()
    }
}


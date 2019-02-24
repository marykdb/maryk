package maryk.core.models

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.ValueDataObject
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.ValueItems
import maryk.lib.time.DateTime
import maryk.test.ByteCollector
import maryk.test.models.TestValueObject
import maryk.test.shouldBe
import kotlin.test.Test

internal class ValueDataModelTest {
    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            TestValueObject,
            ValueDataModel.Model,
            { DefinitionsConversionContext() },
            { converted: ValueDataModel<*, *>, original: ValueDataModel<*, *> ->
                compareDataModels(converted, original)

                // Also test conversion with the generated ValueObject

                @Suppress("UNCHECKED_CAST")
                val convertedValueModel =
                    converted as ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>

                val value = converted.values {
                    ValueItems(
                        convertedValueModel.properties[1]!! withNotNull 5,
                        convertedValueModel.properties[2]!! withNotNull DateTime(2018, 7, 18, 12, 0, 0),
                        convertedValueModel.properties[3]!! withNotNull true
                    )
                }.toDataObject()

                val context = DefinitionsContext()

                val bc = ByteCollector()
                val cache = WriteCache()

                val byteLength = convertedValueModel.calculateProtoBufLength(value, cache, context)
                bc.reserve(byteLength)
                convertedValueModel.writeProtoBuf(value, cache, bc::write, context)
                val convertedValue = convertedValueModel.readProtoBuf(byteLength, bc::read, context).toDataObject()

                convertedValue shouldBe value
            }
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            TestValueObject,
            ValueDataModel.Model,
            { DefinitionsConversionContext() },
            ::compareDataModels
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(
            TestValueObject,
            ValueDataModel.Model,
            { DefinitionsConversionContext() },
            ::compareDataModels
        ) shouldBe """
        name: TestValueObject
        ? 1: int
        : !Number
          required: true
          final: false
          unique: false
          type: SInt32
          maxValue: 6
          random: false
        ? 2: dateTime
        : !DateTime
          required: true
          final: false
          unique: false
          precision: SECONDS
          fillWithNow: false
        ? 3: bool
        : !Boolean
          required: true
          final: false

        """.trimIndent()
    }
}


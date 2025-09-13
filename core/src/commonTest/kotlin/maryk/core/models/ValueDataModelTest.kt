package maryk.core.models

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.ValueDataObject
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.test.ByteCollector
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

internal class ValueDataModelTest {
    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            TestValueObject,
            ValueDataModel.Model,
            { DefinitionsConversionContext() },
            { converted: IsValueDataModel<*, *>, original: IsValueDataModel<*, *> ->
                compareDataModels(converted, original)

                // Also test conversion with the generated ValueObject

                @Suppress("UNCHECKED_CAST")
                val convertedValueModel =
                    converted as ValueDataModel<ValueDataObject, IsValueDataModel<ValueDataObject, *>>

                val value = converted.create {
                    convertedValueModel[1u]!! with 5
                    convertedValueModel[2u]!! with LocalDateTime(2018, 7, 18, 12, 0, 0)
                    convertedValueModel[3u]!! with true
                }.toDataObject()

                val context = DefinitionsContext()

                val bc = ByteCollector()
                val cache = WriteCache()

                @Suppress("UNCHECKED_CAST")
                val serializer = convertedValueModel.Serializer as ObjectDataModelSerializer<ValueDataObject, IsObjectDataModel<ValueDataObject>, IsPropertyContext, IsPropertyContext>

                val byteLength = serializer.calculateObjectProtoBufLength(value, cache, context)
                bc.reserve(byteLength)
                serializer.writeObjectProtoBuf(value, cache, bc::write, context)
                val convertedValue = serializer.readProtoBuf(byteLength, bc::read, context).toDataObject()

                assertEquals(value, convertedValue)
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
        expect(
            """
            name: TestValueObject
            ? 1: int
            : !Number
              required: true
              final: false
              unique: false
              type: SInt32
              maxValue: 6
            ? 2: dateTime
            : !DateTime
              required: true
              final: false
              unique: false
              precision: SECONDS
            ? 3: bool
            : !Boolean
              required: true
              final: false

            """.trimIndent()
        ) {
            checkYamlConversion(
                TestValueObject,
                ValueDataModel.Model,
                { DefinitionsConversionContext() },
                ::compareDataModels
            )
        }
    }
}

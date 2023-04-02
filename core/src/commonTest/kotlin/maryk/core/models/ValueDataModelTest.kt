package maryk.core.models

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.definitions.ValueDataModelDefinition
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.ValueDataObject
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.ValueItems
import maryk.test.ByteCollector
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

internal class ValueDataModelTest {
    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            TestValueObject.Model,
            ValueDataModelDefinition.Model,
            { DefinitionsConversionContext() },
            { converted: ValueDataModelDefinition<*, *>, original: ValueDataModelDefinition<*, *> ->
                compareDataModels(converted, original)

                // Also test conversion with the generated ValueObject

                @Suppress("UNCHECKED_CAST")
                val convertedValueModel =
                    converted as ValueDataModelDefinition<ValueDataObject, IsObjectDataModel<ValueDataObject>>

                val value = converted.properties.values {
                    ValueItems(
                        convertedValueModel.properties[1u]!! withNotNull 5,
                        convertedValueModel.properties[2u]!! withNotNull LocalDateTime(2018, 7, 18, 12, 0, 0),
                        convertedValueModel.properties[3u]!! withNotNull true
                    )
                }.toDataObject()

                val context = DefinitionsContext()

                val bc = ByteCollector()
                val cache = WriteCache()

                @Suppress("UNCHECKED_CAST")
                val serializer = convertedValueModel.properties.Serializer as ObjectDataModelSerializer<ValueDataObject, IsObjectDataModel<ValueDataObject>, IsPropertyContext, IsPropertyContext>

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
            TestValueObject.Model,
            ValueDataModelDefinition.Model,
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
                TestValueObject.Model,
                ValueDataModelDefinition.Model,
                { DefinitionsConversionContext() },
                ::compareDataModels
            )
        }
    }
}

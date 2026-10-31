package maryk.core.models

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.ObjectValues
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.test.ByteCollector
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

private class EmptyValueObject : ValueDataObject(byteArrayOf())

private object EmptyValueDataModel : ValueDataModel<EmptyValueObject, EmptyValueDataModel>("EmptyValue") {
    override fun invoke(values: ObjectValues<EmptyValueObject, EmptyValueDataModel>) = EmptyValueObject()
}

private class SparseValueObject(val value: Int, val flag: Boolean) :
    ValueDataObject(SparseValueDataModel.Serializer.toBytes(value, flag))

private object SparseValueDataModel : ValueDataModel<SparseValueObject, SparseValueDataModel>("SparseValue") {
    val value by number(2u, SparseValueObject::value, type = SInt32)
    val flag by boolean(9u, SparseValueObject::flag)
    override fun invoke(values: ObjectValues<SparseValueObject, SparseValueDataModel>) =
        SparseValueObject(values(2u), values(9u))
}

internal class ValueDataModelTest {
    @Test
    fun generatedValueObjectsOwnTheirCachedValues() {
        val items = MutableValueItems(ValueItem(2u, 42), ValueItem(9u, false))
        val values = ObjectValues(SparseValueDataModel, items)
        val value = ValueDataObjectWithValues(SparseValueDataModel.Serializer.toBytes(42, false), values)
        items[2u] = 43
        assertEquals(42, value.values<Int>(2u))
        (value.values.values as MutableValueItems)[2u] = 44
        assertEquals(42, value.values<Int>(2u))
    }

    @Test
    fun generatedValueObjectsRequireMatchingBytesAndCachedValues() {
        val values = SparseValueDataModel.create { value with 42; flag with false }
        assertFailsWith<IllegalArgumentException> {
            ValueDataObjectWithValues(SparseValueDataModel.Serializer.toBytes(43, false), values)
        }
    }

    @Test
    fun generatedValueObjectCachesUseStoredPrecision() {
        val values = TestValueObject.create {
            int with 1
            dateTime with LocalDateTime(2000, 1, 1, 0, 0, 0, 123)
            bool with true
        }
        val value = ValueDataObjectWithValues(TestValueObject.toBytes(values), values)
        assertEquals(LocalDateTime(2000, 1, 1, 0, 0), value.values<LocalDateTime>(2u))
    }

    @Test
    fun sparsePropertyIndicesRetainValueObjectBytes() {
        val original = SparseValueObject(42, true)
        val values = SparseValueDataModel.create { value with 42; flag with true }
        assertContentEquals(original.toByteArray(), SparseValueDataModel.toBytes(values))
        checkProtoBufConversion(SparseValueDataModel, ValueDataModel.Model, { DefinitionsConversionContext() }, { converted, _ ->
            @Suppress("UNCHECKED_CAST")
            val reconstructed = converted as ValueDataModel<ValueDataObject, IsValueDataModel<ValueDataObject, *>>
            val restored = reconstructed.create { reconstructed[2u]!! with 42; reconstructed[9u]!! with true }.toDataObject()
            assertContentEquals(original.toByteArray(), restored.toByteArray())
        })
    }

    @Test
    fun emptyValueDataModelSerializesToEmptyBytes() {
        assertEquals(0, EmptyValueDataModel.Serializer.byteSize)
        assertEquals(0, EmptyValueDataModel.Serializer.toBytes().size)
    }

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

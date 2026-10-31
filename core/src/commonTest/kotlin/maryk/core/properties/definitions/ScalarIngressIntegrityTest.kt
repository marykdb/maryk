package maryk.core.properties.definitions

import maryk.core.models.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCache
import maryk.json.JsonWriter
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.Option
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFails

private object ErasedIngressRecord : RootDataModel<ErasedIngressRecord>() {
    val flag by boolean(1u, required = false)
    val day by date(2u, required = false)
    val instant by dateTime(3u, required = false)
    val embedded by embed(4u, dataModel = { EmbeddedMarykModel }, required = false)
    val option by enum(5u, enum = Option, required = false)
    val fixed by fixedBytes(6u, byteSize = 4, required = false)
    val flex by flexBytes(7u, required = false)
    val location by geoPoint(8u, required = false)
    val allocations by incrementingMap(9u, keyNumberDescriptor = UInt32, valueDefinition = BooleanDefinition(), required = false)
    val items by list(10u, valueDefinition = BooleanDefinition(), required = false)
    val entries by map(11u, keyDefinition = StringDefinition(), valueDefinition = BooleanDefinition(), required = false)
    val typed by multiType(12u, typeEnum = MarykTypeEnum, required = false)
    val integer by number(13u, type = SInt32, required = false)
    val linked by reference(14u, dataModel = { SimpleMarykModel }, required = false)
    val members by set(15u, valueDefinition = BooleanDefinition(), required = false)
    val text by string(16u, required = false)
    val clock by time(17u, required = false)
    val packed by valueObject(18u, dataModel = TestValueObject, required = false)
    val amount by decimal(19u, scale = 2u, required = false)
}

class ScalarIngressIntegrityTest {
    @Test
    fun nativeTimesRejectFractionalSeconds() {
        for (value in listOf(0.5, 1.5, 86398.5)) {
            assertNull(TimeDefinition().fromNativeType(value), value.toString())
        }
    }

    @Test
    fun erasedScalarValidationRejectsValuesOfAnotherType() {
        val original = ErasedIngressRecord.create(setDefaults = false) {}
        assertEquals(PropertyDefinitionType.entries.toSet(), ErasedIngressRecord.map {
            (it.definition as IsTransportablePropertyDefinitionType<*>).propertyDefinitionType
        }.toSet())
        for (definition in ErasedIngressRecord) {
            val invalid = if (definition.index == 16u) 42 else "wrong scalar type"
            assertFailsWith<ValidationUmbrellaException>(definition.name) {
                original.copyWithValue(definition.index, invalid).validate()
            }
        }
    }

    @Test
    fun erasedCollectionMembersCannotBypassTheirDeclaredTypes() {
        val original = ErasedIngressRecord.create(setDefaults = false) {}
        for ((index, value) in listOf(
            9u to mapOf(1u to "wrong"), 10u to listOf("wrong"),
            11u to mapOf("a" to "wrong"), 15u to setOf("wrong"),
            11u to mapOf(listOf(1) to true),
            10u to listOf(null), 11u to mapOf(null to true), 15u to setOf(null),
        )) {
            assertFailsWith<ValidationUmbrellaException>(index.toString()) {
                original.copyWithValue(index, value).validate()
            }
        }
    }

    @Test
    fun erasedValuesCannotBeCoercedBySerializers() {
        val original = ErasedIngressRecord.create(setDefaults = false) {}
        for (definition in ErasedIngressRecord) {
            val invalid = if (definition.index == 16u) 42 else "wrong scalar type"
            val values = original.copyWithValue(definition.index, invalid)
            assertFails(definition.name) { ErasedIngressRecord.Serializer.writeJson(values, JsonWriter {}) }
            assertFails(definition.name) { ErasedIngressRecord.Serializer.calculateProtoBufLength(values, WriteCache()) }
        }
    }

    @Test
    fun erasedConvenienceEncodersCannotCoerceScalarTypes() {
        for ((typed, invalid) in listOf(
            BooleanDefinition() to "false", StringDefinition() to 42,
            FixedBytesDefinition(byteSize = 4) to "00000000", FlexBytesDefinition() to "00000000",
        )) {
            @Suppress("UNCHECKED_CAST")
            val definition = typed as IsSimpleValueDefinition<Any, IsPropertyContext>
            assertFails(typed.toString()) { definition.toStorageBytes(invalid) }
            assertFails(typed.toString()) { definition.toTransportByteArray(invalid) }
        }
    }
}

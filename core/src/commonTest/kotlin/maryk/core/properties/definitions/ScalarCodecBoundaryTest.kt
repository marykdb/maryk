package maryk.core.properties.definitions

import kotlinx.datetime.LocalDateTime
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.Decimal
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.NumberType
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.lib.exceptions.ParseException
import maryk.test.models.TestValueObject
import maryk.test.models.Option
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private enum class DeclaredScalarOption(override val index: UInt) : IndexedEnumComparable<DeclaredScalarOption> {
    Known(1u), Omitted(2u);
    override val alternativeNames: Set<String>? = null
    companion object : IndexedEnumDefinition<DeclaredScalarOption>(DeclaredScalarOption::class, { listOf(Known) })
}

class ScalarCodecBoundaryTest {
    @Test
    fun enumsRejectCasesTheirDefinitionCannotRead() {
        assertFailsWith<InvalidValueException> {
            EnumDefinition(enum = DeclaredScalarOption).validateWithRef(null, DeclaredScalarOption.Omitted)
        }
        EnumDefinition(enum = Option).validateWithRef(null, Option.UnknownOption(99u, "Future"))
    }

    @Test
    fun floatTextAndLongsRoundCorrectlyNearSinglePrecisionMidpoints() {
        val definition = NumberDefinition(type = Float32)
        for ((text, bits) in listOf(
            "1.0000000596046448" to 0x3f800001,
            "1.0000001788139343" to 0x3f800001,
            "-1.0000000596046448" to 0xbf800001u.toInt(),
            "7.006492321624086e-46" to 1,
            "340282356779733661637539395458142568447" to 0x7f7fffff,
        )) {
            assertEquals(bits, definition.fromString(text).toRawBits(), text)
        }
        val integer = (1L shl 62) + (1L shl 38) + 1L
        assertEquals(0x5e800001, Float32.ofLong(integer).toRawBits())
        assertEquals(0xde800001u.toInt(), Float32.ofLong(-integer).toRawBits())
    }

    @Test
    fun numericValidationRequiresTheDeclaredTypeEvenForUntypedValues() {
        for (type in NumberType.cases()) {
            @Suppress("UNCHECKED_CAST")
            val definition = NumberDefinition(type = type.descriptor()) as IsPropertyDefinition<Any>
            assertFailsWith<InvalidValueException>(type.name) {
                definition.validateWithRef(null, "unparsed number")
            }
        }
        @Suppress("UNCHECKED_CAST")
        val byte = NumberDefinition(type = SInt8) as IsPropertyDefinition<Any>
        assertFailsWith<InvalidValueException> { byte.validateWithRef(null, 128) }
        for (type in listOf(SInt8, SInt16, SInt32)) {
            @Suppress("UNCHECKED_CAST")
            val definition = NumberDefinition(type = type) as IsPropertyDefinition<Any>
            assertFailsWith<InvalidValueException> { definition.validateWithRef(null, 0.5) }
            assertFailsWith<ParseException> { NumberDefinition(type = type).fromNativeType(0.5) }
        }
        assertFailsWith<ParseException> { NumberDefinition(type = SInt16).fromNativeType(32768.0) }
        assertFailsWith<ParseException> { NumberDefinition(type = SInt32).fromNativeType(2147483648.0) }
    }

    @Test
    fun nativeSignedLongConversionRejectsRoundedOverflow() {
        val definition = NumberDefinition(type = SInt64)
        assertFailsWith<ParseException> { definition.fromNativeType(9223372036854775808.0) }
        assertEquals(Long.MIN_VALUE, definition.fromNativeType(-9223372036854775808.0))
        assertEquals(9223372036854774784L, definition.fromNativeType(9223372036854774784.0))
    }

    @Test
    fun nativeUnsignedLongConversionUsesTheUnsignedRange() {
        val definition = NumberDefinition(type = UInt64)
        assertEquals(9223372036854775808uL, definition.fromNativeType(9223372036854775808.0))
        assertEquals(18446744073709549568uL, definition.fromNativeType(18446744073709549568.0))
        assertFailsWith<ParseException> { definition.fromNativeType(18446744073709551616.0) }
        assertFailsWith<ParseException> { definition.fromNativeType(-1.0) }
    }

    @Test
    fun nativeFloatConversionRejectsUnrepresentableNumbers() {
        val definition = NumberDefinition(type = Float32)
        assertEquals(1.5f, definition.fromNativeType(1.5))
        assertFailsWith<ParseException> { definition.fromNativeType(16777217.0) }
        assertFailsWith<ParseException> { definition.fromNativeType(0.1) }
    }

    @Test
    fun parsedFloatValuesHaveTheDeclaredPrecision() {
        val definition = NumberDefinition(type = Float32)
        assertEquals(16777216.0, definition.fromString("16777217").toDouble())
        assertEquals(0.10000000149011612, definition.fromString("0.1").toDouble())
        assertEquals(Float.POSITIVE_INFINITY, definition.fromString("1e100"))
        assertEquals(0.0f, Float32.sum(16777217f, -16777216f))
    }

    @Test
    fun decimalValidationRejectsUnstorableScaleAndWidth() {
        val definition = DecimalDefinition(scale = 2u, byteSize = 1)
        for (value in listOf("1.28", "-1.29", "0.001")) {
            assertFailsWith<InvalidValueException>(value) {
                definition.validateWithRef(null, Decimal.parse(value))
            }
        }
        for (value in listOf("-1.28", "0", "1.270")) {
            definition.validateWithRef(null, Decimal.parse(value))
        }
    }

    @Test
    fun decimalTransportChecksPropertyScaleAndRetainsCompatibleTextBytes() {
        val definition = DecimalDefinition(scale = 2u, byteSize = 1)
        val value = Decimal.parse("1.2")
        assertEquals("1.2", definition.asString(value))
        assertEquals("1.2", definition.toTransportByteArray(value).decodeToString())
        assertEquals("1.200", definition.toTransportByteArray(Decimal.parse("1.200")).decodeToString())
        for (invalid in listOf("0.001", "12.7").map(Decimal::parse)) {
            assertFailsWith<ParseException> { definition.asString(invalid) }
            assertFailsWith<ParseException> { definition.toTransportByteArray(invalid) }
        }
    }

    @Test
    fun narrowVarintsRejectBitsOutsideTheirDeclaredWidth() {
        val byteOverflow = byteArrayOf(0x80.toByte(), 2)
        val shortOverflow = byteArrayOf(0x80.toByte(), 0x80.toByte(), 4)
        for (definition in listOf(NumberDefinition(type = SInt8), NumberDefinition(type = UInt8))) {
            var offset = 0
            assertFailsWith<ParseException> {
                definition.readTransportBytes(byteOverflow.size, { byteOverflow[offset++] })
            }
        }
        for (definition in listOf(NumberDefinition(type = SInt16), NumberDefinition(type = UInt16))) {
            var offset = 0
            assertFailsWith<ParseException> {
                definition.readTransportBytes(shortOverflow.size, { shortOverflow[offset++] })
            }
        }
    }

    @Test
    fun storedMillisecondsRejectFractionsOutsideOneSecond() {
        val definition = DateTimeDefinition(precision = TimePrecision.MILLIS)
        val bytes = definition.toStorageBytes(LocalDateTime(1970, 1, 1, 0, 0))
        for (fraction in listOf(-1, 1000, 2148, Short.MAX_VALUE.toInt(), Short.MIN_VALUE.toInt())) {
            // Independent signed-short storage representation of the fractional field.
            bytes[7] = ((fraction ushr 8) xor 0x80).toByte()
            bytes[8] = fraction.toByte()
            var offset = 0
            assertFailsWith<IllegalArgumentException>("fraction=$fraction") {
                definition.readStorageBytes(bytes.size) { bytes[offset++] }
            }
        }
    }

    @Test
    fun storedBooleansRejectUnknownDiscriminators() {
        val definition = BooleanDefinition()
        for (byte in listOf(2, 127, 128, 255)) {
            assertFailsWith<ParseException>("byte=$byte") {
                definition.readStorageBytes(1) { byte.toByte() }
            }
        }
    }

    @Test
    fun transportBooleansConsumeTheirCompleteVarint() {
        val definition = BooleanDefinition()
        for ((bytes, expected) in listOf(
            byteArrayOf(0x80.toByte(), 1) to true,
            byteArrayOf(0x80.toByte(), 0) to false,
            byteArrayOf(0xff.toByte(), 0xff.toByte(), 1) to true,
        )) {
            var offset = 0
            assertEquals(expected, definition.readTransportBytes(bytes.size, { bytes[offset++] }))
            assertEquals(bytes.size, offset)
        }
    }

    @Test
    fun valueObjectsRejectInvalidSeparators() {
        val definition = ValueObjectDefinition(dataModel = TestValueObject)
        val original = TestValueObject(1, LocalDateTime(2000, 1, 1, 0, 0), true)
        for (position in listOf(4, 12)) {
            for (separator in listOf(0, 2, 255)) {
                val bytes = original.toByteArray().also { it[position] = separator.toByte() }
                var offset = 0
                assertFailsWith<ParseException>("separator=$separator position=$position") {
                    definition.readStorageBytes(bytes.size) { bytes[offset++] }
                }
            }
        }
    }
}

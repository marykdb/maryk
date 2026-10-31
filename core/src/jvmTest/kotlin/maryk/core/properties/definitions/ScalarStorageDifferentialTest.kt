package maryk.core.properties.definitions

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.time.LocalDate as JavaDate
import java.time.LocalDateTime as JavaDateTime
import java.time.ZoneOffset
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.Key
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.test.models.Option
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** JDK encoders and arbitrary-width integer arithmetic are independent codec oracles. */
class ScalarStorageDifferentialTest {
    private val samples = buildList {
        addAll(listOf(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L, 1L))
        var state = 0x4d4152594bL
        repeat(256) {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            add(state)
        }
    }

    @Test
    fun integerStorageAndTransportMatchArbitraryWidthArithmetic() {
        integers(SInt8, samples.map(Long::toByte), true)
        integers(SInt16, samples.map(Long::toShort), true)
        integers(SInt32, samples.map(Long::toInt), true)
        integers(SInt64, samples, true)
        integers(UInt8, samples.map(Long::toUByte), false)
        integers(UInt16, samples.map(Long::toUShort), false)
        integers(UInt32, samples.map(Long::toUInt), false)
        integers(UInt64, samples.map(Long::toULong), false)
    }

    @Test
    fun floatingBytesMatchJdkIeeeEncoders() {
        for (bits in samples) {
            val double = Double.fromBits(bits)
            val float = Float.fromBits(bits.toInt())
            val doubleBytes = ByteBuffer.allocate(8).putDouble(double).array()
            val floatBytes = ByteBuffer.allocate(4).putFloat(float).array()
            for (reversed in listOf(false, true)) {
                assertContentEquals(orderedIeee(doubleBytes, reversed), NumberDefinition(type = Float64, reversedStorage = reversed).toStorageBytes(double))
                assertContentEquals(orderedIeee(floatBytes, reversed), NumberDefinition(type = Float32, reversedStorage = reversed).toStorageBytes(float))
            }
            assertContentEquals(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(double).array(), NumberDefinition(type = Float64).toTransportByteArray(double))
            assertContentEquals(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(float).array(), NumberDefinition(type = Float32).toTransportByteArray(float))
        }
    }

    @Test
    fun temporalGeoDecimalAndPackedBytesMatchIndependentRepresentations() {
        for (sample in samples) {
            val day = sample.toInt()
            val javaDate = JavaDate.ofEpochDay(day.toLong())
            val date = LocalDate(javaDate.year, javaDate.monthValue, javaDate.dayOfMonth)
            assertContentEquals(integerBytes(BigInteger.valueOf(day.toLong()), 4, true), DateDefinition().toStorageBytes(date))
            val seconds = sample % 1_000_000_000_000L
            val nanos = ((sample ushr 1) % 1_000_000_000L).toInt()
            val javaTime = JavaDateTime.ofEpochSecond(seconds, nanos, ZoneOffset.UTC)
            val time = LocalDateTime(javaTime.year, javaTime.monthValue, javaTime.dayOfMonth, javaTime.hour, javaTime.minute, javaTime.second, nanos)
            val dayNanos = (sample ushr 1) % 86_400_000_000_000L
            for (precision in TimePrecision.entries) {
                val fraction = when (precision) {
                    TimePrecision.SECONDS -> byteArrayOf()
                    TimePrecision.MILLIS -> integerBytes(BigInteger.valueOf((nanos / 1_000_000).toLong()), 2, true)
                    TimePrecision.NANOS -> integerBytes(BigInteger.valueOf(nanos.toLong()), 4, false)
                }
                assertContentEquals(integerBytes(BigInteger.valueOf(seconds), 7, true) + fraction, DateTimeDefinition(precision = precision).toStorageBytes(time))
                val unit = when (precision) { TimePrecision.SECONDS -> 1_000_000_000L; TimePrecision.MILLIS -> 1_000_000L; TimePrecision.NANOS -> 1L }
                val width = when (precision) { TimePrecision.SECONDS -> 3; TimePrecision.MILLIS -> 4; TimePrecision.NANOS -> 6 }
                assertContentEquals(integerBytes(BigInteger.valueOf(dayNanos / unit), width, false), TimeDefinition(precision = precision).toStorageBytes(LocalTime.fromNanosecondOfDay(dayNanos)))
            }
            val latitude = (sample % 900_000_001L).toDouble() / 10_000_000
            val longitude = (sample % 1_800_000_001L).toDouble() / 10_000_000
            val point = GeoPoint(latitude, longitude)
            assertContentEquals(integerBytes(BigInteger.valueOf(Math.round(latitude * 10_000_000)), 4, true) +
                integerBytes(BigInteger.valueOf(Math.round(longitude * 10_000_000)), 4, true), GeoPointDefinition().toStorageBytes(point))
            for (width in listOf(8, 16, 128)) {
                assertContentEquals(integerBytes(BigInteger.valueOf(sample), width, true), DecimalDefinition(scale = 18u, byteSize = width).toStorageBytes(Decimal.fromUnscaled(sample, 18u)))
            }
            val packed = TestValueObject(day, time, sample and 1L != 0L)
            assertContentEquals(integerBytes(BigInteger.valueOf(day.toLong()), 4, true) + byteArrayOf(1) +
                integerBytes(BigInteger.valueOf(seconds), 7, true) + byteArrayOf(1, if (sample and 1L == 0L) 0 else 1), packed.toByteArray())
        }
    }

    @Test
    fun stringsBytesReferencesAndEnumsMatchDirectRepresentations() {
        for (text in listOf("", "\u0000", "a\u0000b", "é", "e\u0301", "\uD800\uDC00", "\uFFFF")) {
            assertContentEquals(text.toByteArray(StandardCharsets.UTF_8), StringDefinition().toStorageBytes(text))
        }
        val bytes = ByteArray(SimpleMarykModel.Meta.keyByteSize) { (it * 127).toByte() }
        assertContentEquals(bytes, FixedBytesDefinition(byteSize = bytes.size).toStorageBytes(Bytes(bytes)))
        assertContentEquals(bytes, FlexBytesDefinition().toStorageBytes(Bytes(bytes)))
        assertContentEquals(bytes, ReferenceDefinition(dataModel = { SimpleMarykModel }).toStorageBytes(Key(bytes)))
        assertContentEquals(integerBytes(BigInteger.valueOf(Option.V3.index.toLong()), 2, false), EnumDefinition(enum = Option).toStorageBytes(Option.V3))
        assertContentEquals(byteArrayOf(0), BooleanDefinition().toStorageBytes(false))
        assertContentEquals(byteArrayOf(1), BooleanDefinition().toStorageBytes(true))
    }

    private fun <T : Comparable<T>> integers(type: NumberDescriptor<T>, values: List<T>, signed: Boolean) {
        for (value in values) {
            val integer = BigInteger(value.toString())
            for (reversed in listOf(false, true)) {
                val bytes = integerBytes(integer, type.size, signed).let { if (reversed) it.map { byte -> byte.toInt().inv().toByte() }.toByteArray() else it }
                assertContentEquals(bytes, NumberDefinition(type = type, reversedStorage = reversed).toStorageBytes(value))
            }
            var remaining = if (!signed) integer else if (integer.signum() < 0) integer.negate().shiftLeft(1).subtract(BigInteger.ONE) else integer.shiftLeft(1)
            val transport = buildList {
                do {
                    val digit = remaining.and(BigInteger.valueOf(127)).toInt()
                    remaining = remaining.shiftRight(7)
                    add((digit + if (remaining.signum() == 0) 0 else 128).toByte())
                } while (remaining.signum() != 0)
            }.toByteArray()
            assertContentEquals(transport, NumberDefinition(type = type).toTransportByteArray(value))
        }
    }

    private fun integerBytes(value: BigInteger, width: Int, signed: Boolean): ByteArray {
        val ordinal = if (signed) value.add(BigInteger.ONE.shiftLeft(width * 8 - 1)) else value
        val encoded = ordinal.toByteArray()
        return ByteArray(width) { index -> encoded.getOrElse(encoded.size - width + index) { 0 } }
    }

    private fun orderedIeee(input: ByteArray, reversed: Boolean) = input.mapIndexed { index, byte ->
        val ordered = if (input[0] < 0) byte.toInt().inv() else if (index == 0) byte.toInt() xor 128 else byte.toInt()
        (if (reversed) ordered.inv() else ordered).toByte()
    }.toByteArray()
}

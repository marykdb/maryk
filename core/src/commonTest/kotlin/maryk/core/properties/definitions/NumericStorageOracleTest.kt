package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.UInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.lib.extensions.compare.compareTo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NumericStorageOracleTest {
    @Test
    fun allEightAndSixteenBitValuesMatchIndependentStorageAndVarintBytes() {
        val signedByte = NumberDefinition(type = SInt8)
        val unsignedByte = NumberDefinition(type = UInt8)
        for (ordinal in 0..255) {
            val signed = (ordinal - 128).toByte()
            verify(signedByte, signed, byteArrayOf(ordinal.toByte()), zigZag(signed.toInt()))
            verify(unsignedByte, ordinal.toUByte(), byteArrayOf(ordinal.toByte()), ordinal)
        }
        val signedShort = NumberDefinition(type = SInt16)
        val unsignedShort = NumberDefinition(type = UInt16)
        for (ordinal in 0..65535) {
            val expected = byteArrayOf((ordinal / 256).toByte(), (ordinal % 256).toByte())
            val signed = (ordinal - 32768).toShort()
            verify(signedShort, signed, expected, zigZag(signed.toInt()))
            verify(unsignedShort, ordinal.toUShort(), expected, ordinal)
        }
    }

    @Test
    fun floatingPointStorageMatchesKnownIeeeValuesAndNumericalOrder() {
        val doubles = listOf(
            Double.NEGATIVE_INFINITY to "000fffffffffffff",
            -1.0 to "400fffffffffffff",
            -Double.MIN_VALUE to "7ffffffffffffffe",
            -0.0 to "7fffffffffffffff",
            0.0 to "8000000000000000",
            Double.MIN_VALUE to "8000000000000001",
            1.0 to "bff0000000000000",
            Double.POSITIVE_INFINITY to "fff0000000000000",
        )
        val floats = listOf(
            Float.NEGATIVE_INFINITY to "007fffff",
            -1f to "407fffff",
            -Float.MIN_VALUE to "7ffffffe",
            -0.0f to "7fffffff",
            0.0f to "80000000",
            Float.MIN_VALUE to "80000001",
            1f to "bf800000",
            Float.POSITIVE_INFINITY to "ff800000",
        )
        verifyFloating(NumberDefinition(type = Float64), doubles)
        verifyFloating(NumberDefinition(type = Float64, reversedStorage = true), doubles, reversed = true)
        verifyFloating(NumberDefinition(type = Float32), floats)
        verifyFloating(NumberDefinition(type = Float32, reversedStorage = true), floats, reversed = true)
    }

    private fun zigZag(value: Int) = if (value < 0) -2 * value - 1 else 2 * value

    private fun <T : Comparable<T>> verify(definition: NumberDefinition<T>, value: T, storage: ByteArray, transportValue: Int) {
        assertContentEquals(storage, definition.toStorageBytes(value))
        var offset = 0
        assertEquals(value, definition.readStorageBytes(storage.size) { storage[offset++] })
        val transport = buildList {
            var remaining = transportValue
            while (remaining >= 128) {
                add((remaining % 128 + 128).toByte())
                remaining /= 128
            }
            add(remaining.toByte())
        }.toByteArray()
        assertContentEquals(transport, definition.toTransportByteArray(value))
        offset = 0
        assertEquals(value, definition.readTransportBytes(transport.size, { transport[offset++] }))
        assertEquals(transport.size, offset)
    }

    private fun <T : Comparable<T>> verifyFloating(
        definition: IsSimpleValueDefinition<T, IsPropertyContext>,
        values: List<Pair<T, String>>,
        reversed: Boolean = false,
    ) {
        val encoded = values.map { (value, hex) ->
            val expected = hex.hexToByteArray().let { bytes -> if (reversed) ByteArray(bytes.size) { bytes[it].toInt().inv().toByte() } else bytes }
            definition.toStorageBytes(value).also { assertContentEquals(expected, it) }
        }
        assertEquals(if (reversed) encoded.reversed() else encoded, encoded.sortedWith { a, b -> a.compareTo(b) })
    }
}

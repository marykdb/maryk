package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.types.Decimal
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DecimalDefinitionTest {
    private val definition = DecimalDefinition(
        scale = 2u,
        required = false,
        final = true,
        unique = true,
        minValue = Decimal.parse("-10"),
        maxValue = Decimal.parse("100.00"),
        default = Decimal.parse("12.3"),
    )

    @Test
    fun storageBytesPreserveSignedNumericOrder() {
        val values = listOf("-10.00", "-0.01", "0.00", "12.30", "100.00").map(Decimal::parse)
        val encoded = values.map { value ->
            ByteArray(definition.byteSize).also { bytes ->
                var index = 0
                definition.writeStorageBytes(value) { bytes[index++] = it }
            }
        }

        assertEquals(encoded, encoded.sortedWith(::compareByteArrays))
        values.zip(encoded).forEach { (expected, bytes) ->
            var index = 0
            assertEquals(expected, definition.readStorageBytes(bytes.size) { bytes[index++] })
        }
    }

    @Test
    fun storageByteSizeControlsDecimalRangeAndStoredLength() {
        val definition = DecimalDefinition(scale = 2u, byteSize = 2)
        val values = listOf("-327.68", "-0.01", "0.00", "327.67").map(Decimal::parse)
        val encoded = values.map { value ->
            ByteArray(definition.byteSize).also { bytes ->
                var index = 0
                definition.writeStorageBytes(value) { bytes[index++] = it }
            }
        }

        assertEquals(2, definition.byteSize)
        assertEquals(encoded, encoded.sortedWith(::compareByteArrays))
        values.zip(encoded).forEach { (expected, bytes) ->
            var index = 0
            assertEquals(expected, definition.readStorageBytes(bytes.size) { bytes[index++] })
        }
        assertFailsWith<ParseException> {
            definition.writeStorageBytes(Decimal.parse("327.68")) {}
        }
    }

    @Test
    fun storesValuesBeyondLongRangeInConfiguredWideStorage() {
        val definition = DecimalDefinition(scale = 2u, byteSize = 16)
        val expected = Decimal.parse("123456789012345678901234567890.12")
        val bytes = ByteArray(definition.byteSize)
        var index = 0

        definition.writeStorageBytes(expected) { bytes[index++] = it }

        index = 0
        assertEquals(expected, definition.readStorageBytes(bytes.size) { bytes[index++] })
    }

    @Test
    fun wideStorageSupportsSignedBoundariesAndReversedOrdering() {
        val values = listOf(
            "-170141183460469231731687303715884105728",
            "-1",
            "0",
            "170141183460469231731687303715884105727",
        ).map(Decimal::parse)
        val definition = DecimalDefinition(scale = 0u, byteSize = 16)
        val reversedDefinition = DecimalDefinition(scale = 0u, byteSize = 16, reversedStorage = true)

        fun encode(definition: DecimalDefinition, value: Decimal) = ByteArray(definition.byteSize).also { bytes ->
            var index = 0
            definition.writeStorageBytes(value) { bytes[index++] = it }
        }

        val encoded = values.map { encode(definition, it) }
        val reversed = values.map { encode(reversedDefinition, it) }
        assertEquals(encoded, encoded.sortedWith(::compareByteArrays))
        assertEquals(reversed.reversed(), reversed.sortedWith(::compareByteArrays))
        values.zip(encoded).forEach { (expected, bytes) ->
            var index = 0
            assertEquals(expected, definition.readStorageBytes(bytes.size) { bytes[index++] })
        }
        assertFailsWith<ParseException> {
            definition.writeStorageBytes(Decimal.parse("170141183460469231731687303715884105728")) {}
        }
        assertFailsWith<ParseException> {
            definition.writeStorageBytes(Decimal.parse("-170141183460469231731687303715884105729")) {}
        }
    }

    @Test
    fun maximumConfiguredStorageWidthRoundTripsAndFormats() {
        val value = Decimal.parse("1" + "0".repeat(307))
        val definition = DecimalDefinition(scale = 0u, byteSize = 128)
        val bytes = ByteArray(definition.byteSize)
        val transport = ByteCollector()
        val cache = WriteCacheFailer()
        var index = 0

        definition.writeStorageBytes(value) { bytes[index++] = it }
        transport.reserve(definition.calculateTransportByteLength(value, cache))
        definition.writeTransportBytes(value, cache, transport::write)

        index = 0
        assertEquals(value, definition.readStorageBytes(bytes.size) { bytes[index++] })
        assertEquals(value, definition.readTransportBytes(transport.size, transport::read))
        assertEquals("1" + "0".repeat(307), value.toString())
    }

    @Test
    fun transportUsesExactFixedPointString() {
        val value = Decimal.parse("12.30")
        val collector = ByteCollector()
        val cache = WriteCacheFailer()
        collector.reserve(definition.calculateTransportByteLength(value, cache))
        definition.writeTransportBytes(value, cache, collector::write)

        assertEquals("12.30", collector.bytes!!.decodeToString())
        assertEquals(value, definition.readTransportBytes(collector.size, collector::read))
    }

    @Test
    fun normalizesValuesToDeclaredScaleWithoutRounding() {
        assertEquals(Decimal.parse("12.30"), definition.default)
        assertEquals(Decimal.parse("-10.00"), definition.minValue)
        assertEquals(Decimal.parse("1.20"), definition.fromString("1.2"))
        assertFailsWith<ParseException> { definition.fromString("1.234") }
    }

    @Test
    fun exposesArithmeticRandomAndStorageCapabilities() {
        val definition = DecimalDefinition(
            scale = 2u,
            reversedStorage = true,
        )
        val arithmetic: IsArithmeticDefinition<Decimal> = definition
        val randomizable: IsRandomizableDefinition<Decimal> = definition
        val reversible: IsReversibleStorageDefinition = definition

        assertEquals(true, reversible.reversedStorage)
        assertEquals(Decimal.parse("3.30"), arithmetic.add(Decimal.parse("1.20"), Decimal.parse("2.10")))
        assertEquals(Decimal.parse("1.52"), arithmetic.average(Decimal.parse("3.03"), 2L))
        assertEquals(Decimal.parse("1.50"), arithmetic.average(Decimal.parse("3.01"), 2L))
        assertEquals(Decimal.parse("-1.52"), arithmetic.average(Decimal.parse("-3.03"), 2L))
        assertEquals(Decimal.parse("-1.50"), arithmetic.average(Decimal.parse("-3.01"), 2L))
        assertEquals(2u, randomizable.createRandom().scale)
    }

    @Test
    fun definitionRoundTripsAndStorageShapeIsCompatibilityBoundary() {
        checkProtoBufConversion(definition, DecimalDefinition.Model)
        checkJsonConversion(definition, DecimalDefinition.Model)
        checkYamlConversion(definition, DecimalDefinition.Model)
        checkProtoBufConversion(DecimalDefinition(scale = 2u, byteSize = 2), DecimalDefinition.Model)
        checkJsonConversion(DecimalDefinition(scale = 2u, byteSize = 2), DecimalDefinition.Model)
        checkYamlConversion(DecimalDefinition(scale = 2u, byteSize = 2), DecimalDefinition.Model)

        val reasons = mutableListOf<String>()
        assertTrue(!DecimalDefinition(scale = 3u).compatibleWith(definition) { reasons += it })
        assertTrue(reasons.any { "scale" in it.lowercase() })
        reasons.clear()
        assertTrue(!DecimalDefinition(scale = 2u, byteSize = 4).compatibleWith(definition) { reasons += it })
        assertTrue(reasons.any { "byte size" in it.lowercase() })
    }

    private fun compareByteArrays(left: ByteArray, right: ByteArray): Int {
        for (index in left.indices) {
            val comparison = left[index].toUByte().compareTo(right[index].toUByte())
            if (comparison != 0) return comparison
        }
        return 0
    }
}

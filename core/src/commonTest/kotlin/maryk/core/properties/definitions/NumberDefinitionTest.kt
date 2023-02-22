package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.BIT_32
import maryk.core.protobuf.WireType.VAR_INT
import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class NumberDefinitionTest {
    private val def = NumberDefinition(
        type = UInt32
    )

    private val defReversed = NumberDefinition(
        type = UInt32,
        reversedStorage = true
    )

    private val defMaxDefined = NumberDefinition(
        type = SInt32,
        required = false,
        final = true,
        unique = true,
        minValue = 3254765,
        maxValue = 92763478,
        default = 4444444,
        reversedStorage = false
    )

    private val defFloat32 = NumberDefinition(
        type = Float32
    )

    private val intArray = arrayOf(
        UInt.MIN_VALUE,
        UInt.MAX_VALUE,
        32373957u
    )

    private val floatArray = floatArrayOf(
        323.73957F,
        Float.MIN_VALUE,
        Float.MAX_VALUE,
        1.4E-45F,
        3.4028235E38F,
        323.73957F
    )

    @Test
    fun hasValuesSet() {
        expect(UInt32) { def.type }
    }

    @Test
    fun createRandomNumber() {
        def.createRandom()
    }

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (int in intArray) {
            bc.reserve(
                def.calculateStorageByteLength(int)
            )
            def.writeStorageBytes(int, bc::write)
            expect(int) { def.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValueToStorageBytesAndBack() {
        val bc = ByteCollector()
        bc.reserve(
            def.calculateStorageByteLength(32373957u)
        )
        def.writeStorageBytes(32373957u, bc::write)

        expect("01edfcc5") { bc.bytes?.toHex() }

        expect(32373957u) { def.readStorageBytes(bc.size, bc::read) }
    }

    @Test
    fun convertValueToReversedStorageBytesAndBack() {
        val bc = ByteCollector()
        bc.reserve(
            defReversed.calculateStorageByteLength(32373957u)
        )
        defReversed.writeStorageBytes(32373957u, bc::write)

        expect("fe12033a") { bc.bytes?.toHex() }

        expect(32373957u) { defReversed.readStorageBytes(bc.size, bc::read) }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (value in intArray) {
            bc.reserve(
                def.calculateTransportByteLengthWithKey(1, value, cacheFailer)
            )
            def.writeTransportBytesWithKey(1, value, cacheFailer, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            expect(VAR_INT) { key.wireType }
            expect(1u) { key.tag }
            expect(value) {
                def.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, bc::read),
                    bc::read
                )
            }
            bc.reset()
        }
    }

    @Test
    fun convertFloatValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (value in floatArray) {
            bc.reserve(
                defFloat32.calculateTransportByteLengthWithKey(2, value, cacheFailer)
            )
            defFloat32.writeTransportBytesWithKey(2, value, cacheFailer, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            expect(BIT_32) { key.wireType }
            expect(2u) { key.tag }
            expect(value) {
                defFloat32.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, bc::read),
                    bc::read
                )
            }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (int in intArray) {
            val b = def.asString(int)
            expect(int) { def.fromString(b) }
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        assertFailsWith<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, NumberDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, NumberDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, NumberDefinition.Model)
        checkJsonConversion(this.defMaxDefined, NumberDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, NumberDefinition.Model)
        expect(
            """
            required: false
            final: true
            unique: true
            type: SInt32
            minValue: 3254765
            maxValue: 92763478
            default: 4444444
            reversedStorage: false

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, NumberDefinition.Model)
        }
    }

    @Test
    fun convertNativeType() {
        expect(356725u) { def.fromNativeType(356725.000) }
        expect(38762873u) { def.fromNativeType(38762873) }

        assertFailsWith<ParseException> {
            def.fromNativeType(Long.MAX_VALUE.toDouble())
        }

        assertFailsWith<ParseException> {
            def.fromNativeType(356.9)
        }

        assertFailsWith<ParseException> {
            def.fromNativeType(Double.MAX_VALUE)
        }
    }

    @Test
    fun isCompatible() {
        assertTrue {
            NumberDefinition(type = UInt32, maxValue = 1u).compatibleWith(NumberDefinition(type = UInt32, maxValue = 0u))
        }

        assertFalse {
            NumberDefinition(type = UInt32, reversedStorage = false).compatibleWith(NumberDefinition(type = UInt32, reversedStorage = true))
        }

        assertFalse {
            NumberDefinition(type = UInt32, maxValue = 1u).compatibleWith(NumberDefinition(type = Float32, maxValue = 1.5f))
        }
    }
}

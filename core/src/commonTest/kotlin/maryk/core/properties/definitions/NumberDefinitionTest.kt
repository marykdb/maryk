@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class NumberDefinitionTest {
    private val def = NumberDefinition(
        type = UInt32
    )

    private val defMaxDefined = NumberDefinition(
        type = SInt32,
        indexed = true,
        required = false,
        final = true,
        unique = true,
        minValue = 3254765,
        maxValue = 92763478,
        random = true,
        default = 4444444
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
        def.type shouldBe UInt32
    }

    @Test
    fun createRandomNumber() {
        def.createRandom()
    }

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (it in intArray) {
            bc.reserve(
                def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
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
            key.wireType shouldBe WireType.VAR_INT
            key.tag shouldBe 1
            def.readTransportBytes(
                ProtoBuf.getLength(key.wireType, bc::read),
                bc::read
            ) shouldBe value
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
            key.wireType shouldBe WireType.BIT_32
            key.tag shouldBe 2
            defFloat32.readTransportBytes(
                ProtoBuf.getLength(key.wireType, bc::read),
                bc::read
            ) shouldBe value
            bc.reset()
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (it in intArray) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        shouldThrow<ParseException> {
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
        checkYamlConversion(this.defMaxDefined, NumberDefinition.Model) shouldBe """
        indexed: true
        required: false
        final: true
        unique: true
        type: SInt32
        minValue: 3254765
        maxValue: 92763478
        default: 4444444
        random: true

        """.trimIndent()
    }

    @Test
    fun convertNativeType() {
        def.fromNativeType(356725.000) shouldBe 356725u
        def.fromNativeType(38762873) shouldBe 38762873u

        shouldThrow<ParseException> {
            def.fromNativeType(Long.MAX_VALUE.toDouble())
        }

        shouldThrow<ParseException> {
            def.fromNativeType(356.9)
        }

        shouldThrow<ParseException> {
            def.fromNativeType(Double.MAX_VALUE)
        }
    }
}

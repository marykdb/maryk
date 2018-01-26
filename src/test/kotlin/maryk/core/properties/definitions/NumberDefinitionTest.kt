package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
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
        searchable = false,
        unique = true,
        minValue = 3254765,
        maxValue = 92763478,
        random = true
    )

    private val defFloat32 = NumberDefinition(
        type = Float32
    )

    private val intArray = arrayOf(
        UInt32.MIN_VALUE,
        UInt32.MAX_VALUE,
        32373957.toUInt32()
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
    fun has_values_set() {
        def.type shouldBe UInt32
    }

    @Test
    fun create_random_number() {
        def.createRandom()
    }

    @Test
    fun convert_values_to_storage_bytes_and_back() {
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
    fun convert_values_to_transport_bytes_and_back() {
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
    fun convert_Float_values_to_transport_bytes_and_back() {
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
    fun convert_values_to_String_and_back() {
        for (it in intArray) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun invalid_String_value_should_throw_exception() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, NumberDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, NumberDefinition.Model)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, NumberDefinition.Model)
        checkJsonConversion(this.defMaxDefined, NumberDefinition.Model)
    }
}
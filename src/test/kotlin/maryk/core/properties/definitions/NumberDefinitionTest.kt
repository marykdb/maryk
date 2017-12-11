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

    private val floatArray = arrayOf(
            Float.MIN_VALUE,
            Float.MAX_VALUE,
            323.73957F
    )

    @Test
    fun `has values set`() {
        def.type shouldBe UInt32
    }

    @Test
    fun `create random number`() {
        def.createRandom()
    }

    @Test
    fun `convert values to storage bytes and back`() {
        val bc = ByteCollector()
        intArray.forEach {
            bc.reserve(
                    def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun `convert values to transport bytes and back`() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        intArray.forEach { value ->
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
    fun `convert Float values to transport bytes and back`() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        floatArray.forEach { value ->
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
    fun `convert values to String and back`() {
        intArray.forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun `invalid String value should throw exception`() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(this.def, NumberDefinition)
        checkProtoBufConversion(this.defMaxDefined, NumberDefinition)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, NumberDefinition)
        checkJsonConversion(this.defMaxDefined, NumberDefinition)
    }
}
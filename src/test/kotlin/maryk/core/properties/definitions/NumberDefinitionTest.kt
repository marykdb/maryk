package maryk.core.properties.definitions

import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.fail

internal class NumberDefinitionTest {
    private val def = NumberDefinition(
            name = "test",
            type = UInt32
    )

    private val intArray = arrayOf(
            UInt32.MIN_VALUE,
            UInt32.MAX_VALUE,
            32373957.toUInt32()
    )

    private val defFloat32 = NumberDefinition(
            name = "test",
            type = Float32
    )

    private val floatArray = arrayOf(
            Float.MIN_VALUE,
            Float.MAX_VALUE,
            323.73957F
    )

    @Test
    fun hasValues() {
        def.type shouldBe UInt32
    }

    @Test
    fun createRandom() {
        def.createRandom()
    }

    @Test
    fun convertStorageBytes() {
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
    fun testTransportConversion() {
        val bc = ByteCollector()
        intArray.forEach { value ->
            bc.reserve(
                def.calculateTransportByteLengthWithKey(value, { fail("Should not call") })
            )
            def.writeTransportBytesWithKey(value, { fail("Should not call") }, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.VAR_INT
            key.tag shouldBe -1
            def.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, bc::read),
                    bc::read
            ) shouldBe value
            bc.reset()
        }
    }

    @Test
    fun testFloatTransportConversion() {
        val bc = ByteCollector()
        floatArray.forEach { value ->
            bc.reserve(
                    defFloat32.calculateTransportByteLengthWithKey(value, { fail("Should not call") })
            )
            defFloat32.writeTransportBytesWithKey(value, { fail("Should not call") }, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.BIT_32
            key.tag shouldBe -1
            defFloat32.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, bc::read),
                    bc::read
            ) shouldBe value
            bc.reset()
        }
    }

    @Test
    fun convertString() {
        intArray.forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }
}
package maryk.core.protobuf

import io.kotlintest.matchers.shouldBe
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.initByteArrayByHex
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollector
import kotlin.test.Test

class ProtoBufTest {
    private class PBKey(val tag: Int, val wireType: WireType, val hexBytes: String)

    private val testValues = arrayOf(
            PBKey(4, WireType.VAR_INT, "20"),
            PBKey(8, WireType.LENGTH_DELIMITED, "42"),
            PBKey(15, WireType.BIT_32, "7d"),
            PBKey(16, WireType.START_GROUP, "8301"),
            PBKey(2047, WireType.END_GROUP, "fc7f"),
            PBKey(2048, WireType.BIT_64, "818001"),
            PBKey(262143, WireType.BIT_32, "fdff7f"),
            PBKey(262144, WireType.VAR_INT, "80808001"),
            PBKey(33554431, WireType.START_GROUP, "fbffff7f"),
            PBKey(33554432, WireType.LENGTH_DELIMITED, "8280808001"),
            PBKey(Int.MAX_VALUE, WireType.VAR_INT, "f8ffffff3f")
    )

    @Test
    fun writeKey() {
        fun testGenerateKey(bc: ByteCollector, value: PBKey) {
            bc.reserve(ProtoBuf.calculateKeyLength(value.tag))
            ProtoBuf.writeKey(value.tag, value.wireType, bc::write)
            bc.bytes!!.toHex() shouldBe value.hexBytes
            bc.reset()
        }

        val bc = ByteCollector()

        testValues.forEach {
            testGenerateKey(bc, it)
        }
    }

    @Test
    fun readKey() {
        fun testParseKey(value: PBKey) {
            val bytes = initByteArrayByHex(value.hexBytes)
            var index = 0
            val result = ProtoBuf.readKey { bytes[index++] }
            result.tag shouldBe value.tag
            result.wireType shouldBe value.wireType
        }

        testValues.forEach {
            testParseKey(it)
        }
    }

    @Test
    fun skipField() {
        val bc = ByteCollector()

        bc.reserve(57)

        ProtoBuf.writeKey(22, WireType.VAR_INT, bc::write)
        22.writeVarBytes(bc::write)

        ProtoBuf.writeKey(44, WireType.BIT_64, bc::write)
        4444L.writeBytes(bc::write)

        ProtoBuf.writeKey(55, WireType.LENGTH_DELIMITED, bc::write)
        22.writeVarBytes(bc::write)
        (0 until 22).forEach { bc.write(-1) }

        ProtoBuf.writeKey(66, WireType.START_GROUP, bc::write)

        ProtoBuf.writeKey(1, WireType.VAR_INT, bc::write)
        22.writeVarBytes(bc::write)

        ProtoBuf.writeKey(2, WireType.LENGTH_DELIMITED, bc::write)
        5.writeVarBytes(bc::write)
        (0 until 5).forEach { bc.write(-1) }

        ProtoBuf.writeKey(66, WireType.END_GROUP, bc::write)

        ProtoBuf.writeKey(77, WireType.BIT_32, bc::write)
        333.writeBytes(bc::write)

        fun testSkip(bc: ByteCollector, wireType: WireType, readIndex: Int) {
            ProtoBuf.readKey(bc::read).wireType shouldBe wireType
            ProtoBuf.skipField(wireType, bc::read)

            bc.readIndex shouldBe readIndex
        }

        testSkip(bc, WireType.VAR_INT, 3)
        testSkip(bc, WireType.BIT_64, 13)
        testSkip(bc, WireType.LENGTH_DELIMITED, 38)
        testSkip(bc, WireType.START_GROUP, 51)
        testSkip(bc, WireType.BIT_32, 57)
    }
}
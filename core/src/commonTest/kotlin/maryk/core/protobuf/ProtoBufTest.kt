package maryk.core.protobuf

import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class ProtoBufTest {
    private class PBKey(val tag: UInt, val wireType: WireType, val hexBytes: String)

    private val testValues = arrayOf(
        PBKey(4u, WireType.VAR_INT, "20"),
        PBKey(8u, WireType.LENGTH_DELIMITED, "42"),
        PBKey(15u, WireType.BIT_32, "7d"),
        PBKey(16u, WireType.START_GROUP, "8301"),
        PBKey(2047u, WireType.END_GROUP, "fc7f"),
        PBKey(2048u, WireType.BIT_64, "818001"),
        PBKey(262143u, WireType.BIT_32, "fdff7f"),
        PBKey(262144u, WireType.VAR_INT, "80808001"),
        PBKey(33554431u, WireType.START_GROUP, "fbffff7f"),
        PBKey(33554432u, WireType.LENGTH_DELIMITED, "8280808001"),
        PBKey(UInt.MAX_VALUE, WireType.VAR_INT, "f8ffffff7f")
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

        for (it in testValues) {
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

        for (it in testValues) {
            testParseKey(it)
        }
    }

    @Test
    fun skipField() {
        val bc = ByteCollector()

        bc.reserve(57)

        ProtoBuf.writeKey(22u, WireType.VAR_INT, bc::write)
        22.writeVarBytes(bc::write)

        ProtoBuf.writeKey(44u, WireType.BIT_64, bc::write)
        4444L.writeBytes(bc::write)

        ProtoBuf.writeKey(55u, WireType.LENGTH_DELIMITED, bc::write)
        22.writeVarBytes(bc::write)
        for (it in 0 until 22) {
            bc.write(-1)
        }

        ProtoBuf.writeKey(66u, WireType.START_GROUP, bc::write)

        ProtoBuf.writeKey(1u, WireType.VAR_INT, bc::write)
        22.writeVarBytes(bc::write)

        ProtoBuf.writeKey(2u, WireType.LENGTH_DELIMITED, bc::write)
        5.writeVarBytes(bc::write)
        for (it in 0 until 5) {
            bc.write(-1)
        }

        ProtoBuf.writeKey(66u, WireType.END_GROUP, bc::write)

        ProtoBuf.writeKey(77u, WireType.BIT_32, bc::write)
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

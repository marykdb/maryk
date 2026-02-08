package maryk.core.protobuf

import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.protobuf.WireType.BIT_32
import maryk.core.protobuf.WireType.BIT_64
import maryk.core.protobuf.WireType.END_GROUP
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WireType.START_GROUP
import maryk.core.protobuf.WireType.VAR_INT
import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

class ProtoBufTest {
    private class PBKey(val tag: UInt, val wireType: WireType, val hexBytes: String)

    private val testValues = arrayOf(
        PBKey(4u, VAR_INT, "20"),
        PBKey(8u, LENGTH_DELIMITED, "42"),
        PBKey(15u, BIT_32, "7d"),
        PBKey(16u, START_GROUP, "8301"),
        PBKey(2047u, END_GROUP, "fc7f"),
        PBKey(2048u, BIT_64, "818001"),
        PBKey(262143u, BIT_32, "fdff7f"),
        PBKey(262144u, VAR_INT, "80808001"),
        PBKey(33554431u, START_GROUP, "fbffff7f"),
        PBKey(33554432u, LENGTH_DELIMITED, "8280808001"),
        PBKey(UInt.MAX_VALUE, VAR_INT, "f8ffffff7f")
    )

    @Test
    fun writeKey() {
        fun testGenerateKey(bc: ByteCollector, value: PBKey) {
            bc.reserve(ProtoBuf.calculateKeyLength(value.tag))
            ProtoBuf.writeKey(value.tag, value.wireType, bc::write)
            expect(value.hexBytes) { bc.bytes!!.toHex() }
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
            expect(value.tag) { result.tag }
            expect(value.wireType) { result.wireType }
        }

        for (it in testValues) {
            testParseKey(it)
        }
    }

    @Test
    fun skipField() {
        val bc = ByteCollector()

        bc.reserve(57)

        ProtoBuf.writeKey(22u, VAR_INT, bc::write)
        22.writeVarBytes(bc::write)

        ProtoBuf.writeKey(44u, BIT_64, bc::write)
        4444L.writeBytes(bc::write)

        ProtoBuf.writeKey(55u, LENGTH_DELIMITED, bc::write)
        22.writeVarBytes(bc::write)
        repeat(22) { bc.write(-1) }

        ProtoBuf.writeKey(66u, START_GROUP, bc::write)

        ProtoBuf.writeKey(1u, VAR_INT, bc::write)
        22.writeVarBytes(bc::write)

        ProtoBuf.writeKey(2u, LENGTH_DELIMITED, bc::write)
        5.writeVarBytes(bc::write)
        repeat(5) { bc.write(-1) }

        ProtoBuf.writeKey(66u, END_GROUP, bc::write)

        ProtoBuf.writeKey(77u, BIT_32, bc::write)
        333.writeBytes(bc::write)

        fun testSkip(bc: ByteCollector, wireType: WireType, readIndex: Int) {
            expect(wireType) { ProtoBuf.readKey(bc::read).wireType }
            ProtoBuf.skipField(wireType, bc::read)

            expect(readIndex) { bc.readIndex }
        }

        testSkip(bc, VAR_INT, 3)
        testSkip(bc, BIT_64, 13)
        testSkip(bc, LENGTH_DELIMITED, 38)
        testSkip(bc, START_GROUP, 51)
        testSkip(bc, BIT_32, 57)
    }

    @Test
    fun skipLengthDelimitedWithNegativeLengthShouldFail() {
        val bytes = initByteArrayByHex("ffffffff0f")
        var index = 0
        assertFailsWith<ParseException> {
            ProtoBuf.skipField(LENGTH_DELIMITED) { bytes[index++] }
        }
    }

    @Test
    fun getLengthDelimitedNegativeLengthShouldFail() {
        val bytes = initByteArrayByHex("ffffffff0f")
        var index = 0
        assertFailsWith<ParseException> {
            ProtoBuf.getLength(LENGTH_DELIMITED) { bytes[index++] }
        }
    }
}

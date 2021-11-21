package maryk.core.properties.types

import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class BytesTest {
    private val bytesToTest = arrayOf(
        Bytes("////////"),
        Bytes("AAAAAAA"),
        Bytes("iIiIiIiI")
    )

    @Test
    fun testCompare() {
        expect(0) {
            Bytes("//") compareTo Bytes("//")
        }

        expect(-4) {
            Bytes("AAAA") compareTo Bytes("BBBBBB")
        }
    }

    @Test
    fun hashcode() {
        expect(30) { Bytes("//").hashCode() }
        expect(29791) { Bytes("AAAA").hashCode() }
    }

    @Test
    fun testGet() {
        expect((-1).toByte()) { bytesToTest[0][0] }
        expect(0.toByte()) { bytesToTest[1][3] }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        for (byte in bytesToTest) {
            bc.reserve(byte.size)
            byte.writeBytes(bc::write)
            expect(byte) { Bytes.fromByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testStringConversion() {
        for (bytes in bytesToTest) {
            expect(bytes) {
                Bytes(bytes.toString())
            }
        }
    }

    @Test
    fun testHexConversion() {
        for (bytes in bytesToTest) {
            expect(bytes) {
                Bytes.ofHex(bytes.toHex())
            }
        }
    }

    @Test
    fun testStringConversionExceptions() {
        assertFailsWith<ParseException> {
            Bytes("wrong±")
        }
    }
}

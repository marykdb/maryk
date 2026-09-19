package maryk.core.properties.types

import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class BytesTest {
    private val bytesToTest = arrayOf(
        Bytes("________"),
        Bytes("AAAAAAA"),
        Bytes("iIiIiIiI")
    )

    @Test
    fun testCompare() {
        expect(0) {
            Bytes("_w") compareTo Bytes("_w")
        }

        expect(-4) {
            Bytes("AAAA") compareTo Bytes("BBBB")
        }
    }

    @Test
    fun hashcode() {
        expect(30) { Bytes("_w").hashCode() }
        expect(29791) { Bytes("AAAA").hashCode() }
    }

    @Test
    fun hashcodeDoesNotReflectSourceMutation() {
        val source = byteArrayOf(0)
        val bytes = Bytes(source)
        expect(31) { bytes.hashCode() }

        source[0] = 1
        expect(31) { bytes.hashCode() }
    }

    @Test
    fun hashcodeDoesNotReflectExportedBytesMutation() {
        val bytes = Bytes(byteArrayOf(0))
        val exported = bytes.bytes
        exported[0] = 1

        expect(31) { bytes.hashCode() }
        expect(0.toByte()) { bytes[0] }
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

    @Test
    fun base64ParserWrapsInvalidInput() {
        assertFailsWith<ParseException> {
            parseBase64Bytes("wrong") { throw IllegalArgumentException("invalid") }
        }
    }

    @Test
    fun base64ParserDoesNotWrapFatalErrors() {
        assertFailsWith<Error> {
            parseBase64Bytes("wrong") { throw Error("fatal") }
        }
    }
}

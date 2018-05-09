package maryk.core.properties.types

import maryk.core.properties.ByteCollector
import maryk.lib.exceptions.ParseException
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class BytesTest {
    private val bytesToTest = arrayOf(
        Bytes("////////"),
        Bytes("AAAAAAA"),
        Bytes("iIiIiIiI")
    )

    @Test
    fun testCompare() {
        Bytes("//").compareTo(
            Bytes("//")
        ) shouldBe 0

        Bytes("AAAA").compareTo(
            Bytes("BBBBBB")
        ) shouldBe -4
    }

    @Test
    fun hashcode() {
        Bytes("//").hashCode() shouldBe 30
        Bytes("AAAA").hashCode() shouldBe 29791
    }

    @Test
    fun testGet() {
        bytesToTest[0][0] shouldBe (-1).toByte()
        bytesToTest[1][3] shouldBe 0.toByte()
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        for (it in bytesToTest) {
            bc.reserve(it.size)
            it.writeBytes(bc::write)
            Bytes.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStringConversion() {
        for (it in bytesToTest) {
            Bytes(
                it.toString()
            ) shouldBe it
        }
    }

    @Test
    fun testHexConversion() {
        for (it in bytesToTest) {
            Bytes.ofHex(
                it.toHex()
            ) shouldBe it
        }
    }

    @Test
    fun testStringConversionExceptions() {
        shouldThrow<ParseException> {
            Bytes("wrong±")
        }
    }
}

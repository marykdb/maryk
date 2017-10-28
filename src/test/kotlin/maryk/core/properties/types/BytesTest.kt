package maryk.core.properties.types

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import org.junit.Test

internal class BytesTest {
    private val bytesToTest = arrayOf(
            Bytes.ofBase64String("________"),
            Bytes.ofBase64String("AAAAAAA"),
            Bytes.ofBase64String("iIiIiIiI")
    )

    @Test
    fun testCompare() {
        Bytes.ofBase64String("__").compareTo(
                Bytes.ofBase64String("__")
        ) shouldBe 0

        Bytes.ofBase64String("AAAA").compareTo(
                Bytes.ofBase64String("BBBBBB")
        ) shouldBe -4
    }

    @Test
    fun hashcode() {
        Bytes.ofBase64String("__").hashCode() shouldBe 30
        Bytes.ofBase64String("AAAA").hashCode() shouldBe 29791
    }

    @Test
    fun testGet() {
        bytesToTest[0][0] shouldBe (-1).toByte()
        bytesToTest[1][3] shouldBe 0.toByte()
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        bytesToTest.forEach {
            bc.reserve(it.size)
            it.writeBytes(bc::write)
            Bytes.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStringConversion() {
        bytesToTest.forEach {
            Bytes.ofBase64String(
                    it.toString()
            ) shouldBe it
        }
    }

    @Test
    fun testHexConversion() {
        bytesToTest.forEach {
            Bytes.ofHex(
                    it.toHex()
            ) shouldBe it
        }
    }

    @Test
    fun testStringConversionExceptions() {
        shouldThrow<ParseException> {
            Bytes.ofBase64String("wrong")
        }
    }
}
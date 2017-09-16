package maryk.core.properties.types

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import org.junit.Test

internal class DateTest {
    private val datesToTest = arrayOf(
            Date.nowUTC(),
            Date.MAX,
            Date.MIN
    )

    @Test
    fun compare() {
        Date.MIN.compareTo(Date.MAX) shouldBe -1999999998
        Date.MAX.compareTo(Date.MIN) shouldBe 1999999998
        Date.nowUTC().compareTo(Date.nowUTC()) shouldBe 0
    }

    @Test
    fun testConversion() {
        datesToTest.forEach {
            Date.ofBytes(
                    it.toBytes()
            ) shouldBe it
        }
    }

    @Test
    fun testOffsetConversion() {
        datesToTest.forEach {
            val bytes = ByteArray(22)
            Date.ofBytes(
                    it.toBytes(bytes, 10),
                    10
            ) shouldBe it
        }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        datesToTest.forEach {
            it.writeBytes(bc::reserve, bc::write)
            Date.fromByteReader(bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStringConversion() {
        datesToTest.forEach {
            Date.parse(
                    it.toString(true),
                    iso8601 = true
            ) shouldBe it
        }
    }

    @Test
    fun testStringOptimizedConversion() {
        datesToTest.forEach {
            Date.parse(
                    it.toString(false),
                    iso8601 = false
            ) shouldBe it
        }
    }

    @Test
    fun testStringConversionExceptions() {
        arrayOf(
                "2017-03-32",
                "2017-02-29",
                "2017-02-30",
                "2017-04-31",
                "2017-08-40",
                "2017-13-99"
        ).forEach {
            shouldThrow<ParseException> {
                Date.parse(it)
            }
        }
    }
}
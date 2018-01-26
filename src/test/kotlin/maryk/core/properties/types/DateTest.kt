package maryk.core.properties.types

import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class DateTest {
    private val datesToTest = arrayOf(
            Date.nowUTC(),
            Date.MAX,
            Date.MIN
    )

    @Test
    fun compare() {
        Date.MIN.compareTo(Date.MAX) shouldBe -199999998
        Date.MAX.compareTo(Date.MIN) shouldBe 199999998
        Date.nowUTC().compareTo(Date.nowUTC()) shouldBe 0
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        for (it in datesToTest) {
            bc.reserve(8)
            it.writeBytes(bc::write)
            Date.fromByteReader(bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStringConversion() {
        for (it in datesToTest) {
            Date.parse(
                it.toString()
            ) shouldBe it
        }
    }

    @Test
    fun testStringConversionExceptions() {
        for (it in arrayOf(
            "2017-03-32",
            "2017-02-29",
            "2017-02-30",
            "2017-04-31",
            "2017-08-40",
            "2017-13-99"
        )) {
            shouldThrow<ParseException> {
                Date.parse(it)
            }
        }
    }
}
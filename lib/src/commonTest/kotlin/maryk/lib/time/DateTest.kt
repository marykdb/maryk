package maryk.lib.time

import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class DateTest {
    private val datesToTest = arrayOf(
        Date.nowUTC(),
        Date.MAX,
        Date.MIN
    )

    @Test
    fun compare() {
        expect(-1999998) { Date.MIN.compareTo(Date.MAX) }
        expect(1999998) { Date.MAX.compareTo(Date.MIN) }
        expect(0) { Date.nowUTC().compareTo(Date.nowUTC()) }
    }

    @Test
    fun testStringConversion() {
        for (date in datesToTest) {
            expect(date) { Date.parse(date.toString()) }
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
            assertFailsWith<ParseException> {
                Date.parse(it)
            }
        }
    }
}

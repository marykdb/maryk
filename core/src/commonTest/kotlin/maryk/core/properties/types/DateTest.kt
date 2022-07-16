package maryk.core.properties.types

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import maryk.core.properties.definitions.DateDefinition
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class DateTest {
    private val datesToTest = arrayOf(
        Clock.System.now().toLocalDateTime(TimeZone.UTC).date,
        DateDefinition.MAX,
        DateDefinition.MIN
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        for (date in datesToTest) {
            bc.reserve(4)
            date.writeBytes(bc::write)
            expect(date) { localDateFromByteReader(bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testStringConversion() {
        for (date in datesToTest) {
            expect(date) {
                LocalDate.parse(date.toString())
            }
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
            assertFailsWith<IllegalArgumentException> {
                LocalDate.parse(it)
            }
        }
    }
}
